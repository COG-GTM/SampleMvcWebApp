package com.northwind.scheduling.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Who gets a freed seat (MBA-3829, ADR 0004). Deliberate change from legacy, where usp_CancelBooking promotes
 * nobody (HB-06, MR-12, ODD-09): every seat that is free and not held is offered, in waitlist position order,
 * to the first entry whose accommodation needs fit. An ET25/ET50 entry is skipped (and keeps its place) while no
 * accommodation seat is free; SR/RB/none only need a seat. Only OPEN sessions make offers.
 */
public final class WaitlistOfferRules {

    private WaitlistOfferRules() {
    }

    /** Entries to offer a seat to now, in position order; one per free, unheld seat. */
    public static List<WaitlistSlot> select(SessionStatus status, SessionCapacity seats, List<WaitlistSlot> waitlist) {
        List<WaitlistSlot> offers = new ArrayList<>();
        if (status == null || !status.acceptsBookings()) {
            return offers;
        }
        int free = seats.seatsAvailable();
        int accommodationFree = seats.accommodationAvailable();
        List<WaitlistSlot> ordered = waitlist.stream()
                .sorted(Comparator.comparingInt(WaitlistSlot::position)).toList();
        for (WaitlistSlot w : ordered) {
            if (free == 0) {
                break;
            }
            if (w.needsAccommodationSeat()) {
                if (accommodationFree == 0) {
                    continue;
                }
                accommodationFree--;
            }
            offers.add(w);
            free--;
        }
        return offers;
    }
}
