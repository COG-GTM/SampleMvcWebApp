package com.northwind.assessment.scheduling.config;

import com.northwind.assessment.scheduling.domain.AccommodationFloorPolicy;
import com.northwind.assessment.scheduling.domain.RetakeEligibilityPolicy;
import com.northwind.assessment.scheduling.domain.WaitlistOfferPolicy;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RulesConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public RetakeEligibilityPolicy retakeEligibilityPolicy(SchedulingProperties properties) {
        return new RetakeEligibilityPolicy(properties.rules().retakeDayCounting());
    }

    @Bean
    public WaitlistOfferPolicy waitlistOfferPolicy(SchedulingProperties properties) {
        return new WaitlistOfferPolicy(properties.waitlist().offerTtl());
    }

    @Bean
    public AccommodationFloorPolicy accommodationFloorPolicy(SchedulingProperties properties) {
        return properties.rules().accommodationFloorPolicy();
    }
}
