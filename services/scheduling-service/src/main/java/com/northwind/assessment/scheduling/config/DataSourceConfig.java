package com.northwind.assessment.scheduling.config;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Two connections:
 * <ul>
 *   <li>primary: the service's own {@code Scheduling} database (Flyway, all reads/writes);</li>
 *   <li>{@code legacyJdbcClient}: read-only login to AssessmentDelivery, used only by the importer.
 *       The pool is lazy, so the service starts even when the legacy database is unreachable.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);
    private static final Pattern DATABASE_NAME = Pattern.compile("(?i)(databaseName|database)=([^;]+)");
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_]+");

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties, SchedulingProperties scheduling) {
        if (scheduling.datasource().createDatabase()) {
            createDatabaseIfMissing(properties.determineUrl(), properties.determineUsername(), properties.determinePassword());
        }
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    /** The default JdbcClient: always the Scheduling database. */
    @Bean
    @Primary
    public JdbcClient jdbcClient(DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }

    @Bean
    @Qualifier("legacy")
    public JdbcClient legacyJdbcClient(SchedulingProperties scheduling) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("legacy-readonly");
        ds.setJdbcUrl(scheduling.legacy().url());
        ds.setUsername(scheduling.legacy().username());
        ds.setPassword(scheduling.legacy().password());
        ds.setReadOnly(true);
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(0);
        ds.setInitializationFailTimeout(-1);
        return JdbcClient.create((DataSource) ds);
    }

    static void createDatabaseIfMissing(String url, String user, String password) {
        Matcher m = DATABASE_NAME.matcher(url);
        if (!m.find()) {
            return;
        }
        String db = m.group(2).trim();
        if (!SAFE_NAME.matcher(db).matches()) {
            throw new IllegalStateException("Refusing to create database with unexpected name: " + db);
        }
        if (db.equalsIgnoreCase("AssessmentDelivery")) {
            throw new IllegalStateException("scheduling-service must not use the AssessmentDelivery database");
        }
        String masterUrl = m.replaceFirst(m.group(1) + "=master");
        try (Connection c = DriverManager.getConnection(masterUrl, user, password);
             Statement st = c.createStatement()) {
            st.execute("IF DB_ID(N'" + db + "') IS NULL CREATE DATABASE [" + db + "]");
            log.info("Scheduling database '{}' is present", db);
        } catch (SQLException e) {
            log.warn("Could not ensure database '{}' exists ({}); continuing and letting Flyway report", db, e.getMessage());
        }
    }
}
