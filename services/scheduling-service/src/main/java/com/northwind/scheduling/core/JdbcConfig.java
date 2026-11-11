package com.northwind.scheduling.core;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class JdbcConfig {

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new RetryingJdbcTemplate(dataSource);
    }
}
