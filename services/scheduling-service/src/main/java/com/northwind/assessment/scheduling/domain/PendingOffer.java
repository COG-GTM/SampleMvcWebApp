package com.northwind.assessment.scheduling.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/** A PENDING seat offer to a waitlisted booking: holds one seat of its kind until accepted, declined or expired. */
public record PendingOffer(long offerId, long bookingId, SeatKind seatKind, LocalDateTime offeredAt,
                           LocalDateTime expiresAt) {

    public PendingOffer {
        Objects.requireNonNull(seatKind, "seatKind");
        Objects.requireNonNull(offeredAt, "offeredAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** Expired once {@code now} reaches {@code expiresAt}, whether or not the expiry job has run yet. */
    public boolean isExpiredAt(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }
}
