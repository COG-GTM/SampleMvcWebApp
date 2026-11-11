package com.northwind.assessment.scheduling.relay;

import com.northwind.assessment.scheduling.config.SchedulingProperties;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The relay's own connection to AssessmentDelivery (login ad_sched_relay). Every physical connection marks its
 * session {@code writer = scheduling-relay} (read-only, so it cannot be cleared), which makes
 * trg_Booking_SeatSync / trg_ExamSession_Update skip their side effects for relayed writes. Not exposed as a
 * DataSource / transaction manager bean, so @Transactional keeps meaning the Scheduling database.
 */
@Configuration(proxyBeanMethods = false)
public class RelayConfig {

    public static final String WRITER = "scheduling-relay";

    public record LegacyRelayDb(JdbcClient jdbc, TransactionTemplate tx) {
    }

    @Bean(destroyMethod = "")
    public LegacyRelayDb legacyRelayDb(SchedulingProperties scheduling) {
        SchedulingProperties.Relay r = scheduling.relay();
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("legacy-relay");
        ds.setJdbcUrl(r.url());
        ds.setUsername(r.username());
        ds.setPassword(r.password());
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(0);
        ds.setInitializationFailTimeout(-1);
        ds.setConnectionInitSql("EXEC sp_set_session_context @key = N'writer', @value = N'" + WRITER + "', @read_only = 1");
        return new LegacyRelayDb(JdbcClient.create((DataSource) ds),
                new TransactionTemplate(new DataSourceTransactionManager(ds)));
    }
}
