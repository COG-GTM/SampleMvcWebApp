package com.northwind.assessment.scheduling.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs {@link SeatOfferExpiryJob} every {@code scheduling.waitlist.expiry-poll-ms} unless {@code scheduling.waitlist.expiry-enabled=false}. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "scheduling.waitlist.expiry-enabled", havingValue = "true", matchIfMissing = true)
public class SeatOfferExpiryScheduler {

    private final SeatOfferExpiryJob job;

    public SeatOfferExpiryScheduler(SeatOfferExpiryJob job) {
        this.job = job;
    }

    @Scheduled(fixedDelayString = "${scheduling.waitlist.expiry-poll-ms:60000}", initialDelay = 5000)
    public void poll() {
        job.runOnce();
    }
}
