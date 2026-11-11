package com.northwind.scheduling.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.northwind.scheduling.domain.AccommodationFloorPolicy;
import com.northwind.scheduling.domain.RetakeDayCounting;
import com.northwind.scheduling.domain.RetakeEligibilityPolicy;
import com.northwind.scheduling.domain.SeatFormula;

/** Wires the ADR 0002 rule switches ({@code scheduling.rules.*}) into the Spring-free domain. */
@Configuration
public class RulesConfig {

    @Bean
    public RetakeEligibilityPolicy retakeEligibilityPolicy(@Value("${scheduling.rules.retake-days:calendar}") String days) {
        return new RetakeEligibilityPolicy(RetakeDayCounting.parse(days));
    }

    @Bean
    public AccommodationFloorPolicy accommodationFloorPolicy(
            @Value("${scheduling.rules.accommodation-floor:allocated}") String floor) {
        return AccommodationFloorPolicy.parse(floor);
    }

    @Bean
    public SeatFormula seatFormula(@Value("${scheduling.rules.seat-formula:standard}") String formula) {
        return SeatFormula.parse(formula);
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    /** Same wire format as booking-api (DateUtils.ISO_DATE_TIME): seconds always present, no zone. */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer isoDates() {
        return b -> b
                .serializerByType(LocalDateTime.class,
                        new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                .serializerByType(LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ISO_LOCAL_DATE));
    }
}
