package com.samplemvcwebapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SampleMvcWebAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleMvcWebAppApplication.class, args);
    }
}
