package com.samplemvcwebapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:contextLoads;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE")
class SampleMvcWebAppApplicationTests {

    @Test
    void contextLoads() {
    }
}
