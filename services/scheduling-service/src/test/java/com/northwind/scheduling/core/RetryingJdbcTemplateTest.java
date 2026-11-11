package com.northwind.scheduling.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.support.SQLStateSQLExceptionTranslator;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetryingJdbcTemplateTest {

    @AfterEach
    void clear() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    /** A read that is a deadlock victim {@code failures} times (wrapped "Done status" form), then returns no rows. */
    static RetryingJdbcTemplate template(int failures, AtomicInteger executions) throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection c = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(ds.getConnection()).thenReturn(c);
        when(c.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenAnswer(i -> {
            if (executions.incrementAndGet() <= failures) {
                throw new SQLException(DeadlockRetryTest.WRAPPED, "S0001", 0);
            }
            return rs;
        });
        when(rs.next()).thenReturn(false);
        RetryingJdbcTemplate t = new RetryingJdbcTemplate(ds);
        t.setExceptionTranslator(new SQLStateSQLExceptionTranslator());
        return t;
    }

    @Test
    void autocommitReadIsRetriedOnDeadlock() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        assertThat(template(2, executions).queryForList("SELECT SessionId FROM dbo.ExamSession WHERE SessionId = ?", 7))
                .isEmpty();
        assertThat(executions).hasValue(3);
    }

    @Test
    void readGivesUpAfterBoundedAttempts() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        RetryingJdbcTemplate t = template(99, executions);
        assertThatThrownBy(() -> t.queryForList("SELECT 1 WHERE 1 = ?", 1)).isInstanceOf(DataAccessException.class);
        assertThat(executions).hasValue(DeadlockRetry.ATTEMPTS);
    }

    @Test
    void statementInsideTransactionIsLeftToTheTransactionRetry() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        RetryingJdbcTemplate t = template(1, executions);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(() -> t.queryForList("SELECT 1 WHERE 1 = ?", 1)).isInstanceOf(DataAccessException.class);
        assertThat(executions).hasValue(1);
    }
}
