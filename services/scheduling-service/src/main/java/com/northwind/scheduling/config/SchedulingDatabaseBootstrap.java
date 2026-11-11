package com.northwind.scheduling.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Flyway migrates a database but does not create it. Before the pool starts this connects to
 * {@code master} on the same server and creates the configured database (default "Scheduling")
 * when it does not exist yet, so a fresh SQL Server only needs the service started.
 */
@Configuration
public class SchedulingDatabaseBootstrap {
    private static final Logger log = LoggerFactory.getLogger(SchedulingDatabaseBootstrap.class);
    private static final Pattern DB_NAME = Pattern.compile("(?i)(;databaseName=)([^;]+)");

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties props,
                                 @Value("${scheduling.database.create-if-missing:true}") boolean createIfMissing,
                                 @Value("${scheduling.database.wait-seconds:90}") int waitSeconds) {
        if (createIfMissing) {
            createIfMissing(props.determineUrl(), props.determineUsername(), props.determinePassword(), waitSeconds);
        }
        return props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    static void createIfMissing(String url, String user, String password, int waitSeconds) {
        Matcher m = DB_NAME.matcher(url);
        if (!m.find()) {
            return;
        }
        String db = m.group(2);
        if (!db.matches("[A-Za-z0-9_]+")) {
            throw new IllegalStateException("Unsupported database name " + db);
        }
        String masterUrl = m.replaceFirst("$1master");
        long deadline = System.currentTimeMillis() + waitSeconds * 1000L;
        while (true) {
            try (Connection c = DriverManager.getConnection(masterUrl, user, password);
                 Statement st = c.createStatement()) {
                st.execute("IF DB_ID(N'" + db + "') IS NULL CREATE DATABASE [" + db + "]");
                log.info("Database {} is present", db);
                return;
            } catch (SQLException e) {
                if (System.currentTimeMillis() > deadline) {
                    throw new IllegalStateException("Cannot create/verify database " + db + ": " + e.getMessage(), e);
                }
                log.info("Waiting for SQL Server ({})", e.getMessage());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ie);
                }
            }
        }
    }
}
