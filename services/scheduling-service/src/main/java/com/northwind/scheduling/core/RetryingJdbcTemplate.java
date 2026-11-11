package com.northwind.scheduling.core;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;

/**
 * JdbcTemplate that re-runs an autocommit statement (no surrounding transaction) when it is a 1205 victim, e.g. a
 * session read whose COUNT subqueries collide with a cancel's ExamSession update. Each autocommit statement is its own
 * transaction, so re-running it is a whole-transaction retry. Statements inside a transaction are not retried here:
 * the victim's transaction is already rolled back, and {@link SchedulingCommands} re-runs the whole transaction.
 * All JdbcTemplate query/update convenience methods funnel into the four overridden methods.
 */
public class RetryingJdbcTemplate extends JdbcTemplate {

    public RetryingJdbcTemplate(DataSource dataSource) {
        super(dataSource);
    }

    private <T> T retry(String sql, java.util.function.Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return work.get();
        }
        return DeadlockRetry.run(sql == null ? "statement" : sql.length() > 60 ? sql.substring(0, 60) + "..." : sql, work);
    }

    @Override
    public <T> T query(String sql, ResultSetExtractor<T> rse) {
        return retry(sql, () -> super.query(sql, rse));
    }

    @Override
    public <T> T query(PreparedStatementCreator psc, PreparedStatementSetter pss, ResultSetExtractor<T> rse) {
        return retry(sqlOf(psc), () -> super.query(psc, pss, rse));
    }

    @Override
    public int update(String sql) {
        return retry(sql, () -> super.update(sql));
    }

    @Override
    protected int update(PreparedStatementCreator psc, PreparedStatementSetter pss) {
        return retry(sqlOf(psc), () -> super.update(psc, pss));
    }

    private static String sqlOf(PreparedStatementCreator psc) {
        return psc instanceof org.springframework.jdbc.core.SqlProvider p ? p.getSql() : null;
    }
}
