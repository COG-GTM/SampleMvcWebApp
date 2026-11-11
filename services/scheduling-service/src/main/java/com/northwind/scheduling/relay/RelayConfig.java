package com.northwind.scheduling.relay;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AssessmentDelivery connection for the outbox relay, as ad_sched_relay (db/security/036_component_logins.sql).
 * Not a Spring DataSource bean, so the Scheduling DataSource stays the only auto-configured one.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "scheduling.relay.enabled", havingValue = "true")
public class RelayConfig {

    @Bean
    LegacyRelay legacyRelay(@Value("${scheduling.relay.url}") String url,
                            @Value("${scheduling.relay.username}") String user,
                            @Value("${scheduling.relay.password}") String password) {
        DriverManagerDataSource ds = new DriverManagerDataSource(url, user, password);
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        Properties p = new Properties();
        p.setProperty("loginTimeout", "5");
        ds.setConnectionProperties(p);
        return new LegacyRelay(new JdbcTemplate(ds));
    }
}
