package com.northwind.scheduling.reference;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Read-only connection to AssessmentDelivery, used only by the reference-data relay and the initial import.
 * Deliberately not a DataSource bean, so the primary (Scheduling) DataSource stays auto-configured.
 */
@Component
public class SourceDatabase {

    private final String url;
    private final String user;
    private final String password;
    private volatile HikariDataSource ds;
    private volatile JdbcTemplate jdbc;

    public SourceDatabase(@Value("${scheduling.source.url}") String url,
                          @Value("${scheduling.source.username}") String user,
                          @Value("${scheduling.source.password}") String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public synchronized JdbcTemplate jdbc() {
        if (jdbc == null) {
            HikariConfig c = new HikariConfig();
            c.setPoolName("assessment-delivery-source");
            c.setJdbcUrl(url);
            c.setUsername(user);
            c.setPassword(password);
            c.setMaximumPoolSize(2);
            c.setReadOnly(true);
            ds = new HikariDataSource(c);
            jdbc = new JdbcTemplate(ds);
        }
        return jdbc;
    }

    @PreDestroy
    public synchronized void close() {
        if (ds != null) {
            ds.close();
        }
    }
}
