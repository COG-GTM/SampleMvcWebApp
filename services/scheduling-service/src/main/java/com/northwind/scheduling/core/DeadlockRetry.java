package com.northwind.scheduling.core;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;

import java.util.function.Supplier;

/**
 * Bounded re-run of a whole unit of work when SQL Server picks it as a 1205 deadlock victim. Shared by command
 * transactions ({@link SchedulingCommands}) and autocommit statements ({@link RetryingJdbcTemplate}), so read endpoints
 * are retried exactly like commands. READ_COMMITTED_SNAPSHOT is deliberately not used (legacy read semantics).
 */
public final class DeadlockRetry {

    public static final int ATTEMPTS = 5;

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DeadlockRetry.class);

    private DeadlockRetry() {
    }

    public static <T> T run(String what, Supplier<T> work) {
        for (int attempt = 1; ; attempt++) {
            try {
                return work.get();
            } catch (RuntimeException e) {
                if (attempt >= ATTEMPTS || !isDeadlock(e)) {
                    throw e;
                }
                LOG.info("Deadlock victim ({}), retrying (attempt {} of {})", what, attempt + 1, ATTEMPTS);
                try {
                    Thread.sleep(10L * attempt + java.util.concurrent.ThreadLocalRandom.current().nextInt(20));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    /**
     * True if SQL Server chose this work as a 1205 deadlock victim. Walks causes, SQLException next-exceptions
     * and suppressed exceptions, because mssql-jdbc can report the victim in a wrapped form ("An error occurred during
     * the current command (Done status 0). Transaction ... was deadlocked ...") whose getErrorCode() is not 1205.
     */
    public static boolean isDeadlock(Throwable e) {
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.Deque<Throwable> todo = new java.util.ArrayDeque<>();
        todo.push(e);
        while (!todo.isEmpty()) {
            Throwable t = todo.pop();
            if (!seen.add(t)) {
                continue;
            }
            if (t instanceof com.microsoft.sqlserver.jdbc.SQLServerException se && se.getSQLServerError() != null
                    && se.getSQLServerError().getErrorNumber() == 1205) {
                return true;
            }
            if (t instanceof java.sql.SQLException sql) {
                if (sql.getErrorCode() == 1205 || mentionsDeadlockVictim(sql.getMessage())) {
                    return true;
                }
                if (sql.getNextException() != null) {
                    todo.push(sql.getNextException());
                }
            }
            if ("DeadlockLoserDataAccessException".equals(t.getClass().getSimpleName())
                    || ((t instanceof CannotAcquireLockException || t instanceof PessimisticLockingFailureException)
                    && mentionsDeadlockVictim(t.getMessage()))) {
                return true;
            }
            if (t.getCause() != null) {
                todo.push(t.getCause());
            }
            for (Throwable s : t.getSuppressed()) {
                todo.push(s);
            }
        }
        return false;
    }

    private static boolean mentionsDeadlockVictim(String message) {
        return message != null && message.toLowerCase(java.util.Locale.ROOT).contains("deadlocked");
    }
}
