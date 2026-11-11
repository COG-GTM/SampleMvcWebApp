package com.northwind.scheduling.domain;

/** A WAITLISTED booking without a pending offer, as seen by {@link WaitlistOfferRules}. */
public record WaitlistSlot(int bookingId, int position, String accommodationCode) {

    public boolean needsAccommodationSeat() {
        return AccommodationCodes.isExtraTime(accommodationCode);
    }
}
