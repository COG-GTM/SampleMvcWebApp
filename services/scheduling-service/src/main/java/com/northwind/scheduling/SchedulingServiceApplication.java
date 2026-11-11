package com.northwind.scheduling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SchedulingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulingServiceApplication.class, args);
    }
}
