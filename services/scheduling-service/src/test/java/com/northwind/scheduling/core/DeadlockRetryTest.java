package com.northwind.scheduling.core;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeadlockRetryTest {

    static final String WRAPPED = "An error occurred during the current command (Done status 0). Transaction (Process ID 63) "
            + "was deadlocked on lock resources with another process and has been chosen as the deadlock victim. "
            + "Rerun the transaction.";

    /** Runs the callback directly, like a TransactionTemplate whose transaction rolled back on the exception. */
    static final class DirectTx extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction((TransactionStatus) null);
        }
    }

    static RuntimeException wrappedDeadlock() {
        SQLException driver = new SQLException(WRAPPED, "S0001", 0);
        return new UncategorizedSQLException("PreparedStatementCallback", "UPDATE dbo.ExamSession ...", driver);
    }

    SchedulingCommands commands() {
        return new SchedulingCommands(null, new DirectTx(), null, null, null, false);
    }

    @Test
    void classifiesWrappedAndTranslatedDeadlocks() {
        assertThat(SchedulingCommands.isDeadlock(wrappedDeadlock())).isTrue();
        SQLException chained = new SQLException("An error occurred during the current command (Done status 0).", "S0001", 0);
        chained.setNextException(new SQLException("victim", "40001", 1205));
        assertThat(SchedulingCommands.isDeadlock(new UncategorizedSQLException("t", "sql", chained))).isTrue();
        assertThat(SchedulingCommands.isDeadlock(new CannotAcquireLockException("x", new SQLException("v", "40001", 1205))))
                .isTrue();
        assertThat(SchedulingCommands.isDeadlock(new CannotAcquireLockException("Transaction was deadlocked"))).isTrue();
        assertThat(SchedulingCommands.isDeadlock(new UncategorizedSQLException("t", "sql",
                new SQLException("Lock request time out period exceeded.", "S0001", 1222)))).isFalse();
        assertThat(SchedulingCommands.isDeadlock(new IllegalStateException("x"))).isFalse();
    }

    @Test
    void retriesWholeTransactionOnWrapped1205() {
        AtomicInteger calls = new AtomicInteger();
        String result = commands().inTx(s -> {
            if (calls.incrementAndGet() < 3) {
                throw wrappedDeadlock();
            }
            return "committed";
        });
        assertThat(result).isEqualTo("committed");
        assertThat(calls).hasValue(3);
    }

    @Test
    void givesUpAfterBoundedAttemptsAndNeverRetriesOtherErrors() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> commands().inTx(s -> {
            calls.incrementAndGet();
            throw wrappedDeadlock();
        })).isInstanceOf(UncategorizedSQLException.class);
        assertThat(calls).hasValue(SchedulingCommands.DEADLOCK_ATTEMPTS);

        AtomicInteger other = new AtomicInteger();
        assertThatThrownBy(() -> commands().inTx(s -> {
            other.incrementAndGet();
            throw new IllegalStateException("not a deadlock");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(other).hasValue(1);
    }
}
