package com.northwind.assessment.scheduling.domain;

import java.time.LocalDateTime;

/**
 * What the waitlist drawer shows for one position: {@code OFFERED} (holds a pending offer), {@code SKIPPED}
 * (needs an accommodation seat but none is free, a later position was offered instead; keeps the position) or
 * {@code WAITING}.
 */
public record WaitlistOfferState(long bookingId, int position, State state, String accommodationCode,
                                 PendingOffer offer) {

    public enum State { OFFERED, SKIPPED, WAITING }

    public LocalDateTime offerExpiresAt() {
        return offer == null ? null : offer.expiresAt();
    }

    public String skipReason() {
        return state == State.SKIPPED ? "Needs " + accommodationCode + " accommodation seat" : null;
    }
}
