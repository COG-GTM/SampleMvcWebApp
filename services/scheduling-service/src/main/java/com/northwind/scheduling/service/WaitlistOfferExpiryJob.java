package com.northwind.scheduling.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Expires PENDING seat offers past scheduling.waitlist.offer-ttl and offers the seat on (MBA-3829). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "scheduling.waitlist.expiry-enabled", havingValue = "true", matchIfMissing = true)
public class WaitlistOfferExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(WaitlistOfferExpiryJob.class);

    private final SchedulingService service;

    public WaitlistOfferExpiryJob(SchedulingService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${scheduling.waitlist.expiry-poll:PT1M}", initialDelayString = "${scheduling.waitlist.expiry-poll:PT1M}")
    public void expireDueOffers() {
        try {
            int n = 0;
            for (int offerId : service.dueOfferIds()) {
                if (service.expireOffer(offerId)) {
                    n++;
                }
            }
            if (n > 0) {
                log.info("waitlist offers expired={}", n);
            }
        } catch (RuntimeException e) {
            log.warn("waitlist offer expiry failed: {}", e.toString());
        }
    }
}
