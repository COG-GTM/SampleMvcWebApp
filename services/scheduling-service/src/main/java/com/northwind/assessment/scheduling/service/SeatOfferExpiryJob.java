package com.northwind.assessment.scheduling.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Expires PENDING seat offers past {@code expires_at} and offers the seats on (ADR 0003), one session per
 * transaction. Triggered by {@link SeatOfferExpiryScheduler}; tests call {@link #runOnce} directly.
 */
@Component
public class SeatOfferExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(SeatOfferExpiryJob.class);

    private final SeatOfferService offers;

    public SeatOfferExpiryJob(SeatOfferService offers) {
        this.offers = offers;
    }

    /** @return offers expired in this run */
    public int runOnce() {
        int expired = 0;
        for (int sessionId : offers.sessionsWithExpiredOffers()) {
            try {
                expired += offers.expireDue(sessionId);
            } catch (RuntimeException e) {
                log.warn("seat offer expiry failed for session {}: {}", sessionId, e.getMessage());
            }
        }
        if (expired > 0) {
            log.info("seat offer expiry: {} offer(s) expired", expired);
        }
        return expired;
    }
}
