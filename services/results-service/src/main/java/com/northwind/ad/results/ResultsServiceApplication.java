package com.northwind.ad.results;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Score ingestion. Delivery partners post results one at a time or drop a CSV score file.
 */
@SpringBootApplication
public class ResultsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResultsServiceApplication.class, args);
    }
}
