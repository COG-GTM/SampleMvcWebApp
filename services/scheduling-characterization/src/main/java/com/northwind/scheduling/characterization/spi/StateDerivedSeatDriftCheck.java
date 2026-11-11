package com.northwind.scheduling.characterization.spi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import com.northwind.scheduling.characterization.spi.Model.BookingState;
import com.northwind.scheduling.characterization.spi.Model.ExamSessionState;
import com.northwind.scheduling.characterization.spi.Model.SeatDrift;

/**
 * Target-agnostic seat-drift check derived from {@link SchedulingTarget} state reads, using the formula of
 * db/checks/seat-drift.sql. Any target gets this for free; the legacy target cross-checks it against the SQL file.
 */
public final class StateDerivedSeatDriftCheck implements SeatDriftCheck {

    private final SchedulingTarget target;
    private final Supplier<Collection<Integer>> sessionIds;

    public StateDerivedSeatDriftCheck(SchedulingTarget target, Supplier<Collection<Integer>> sessionIds) {
        this.target = target;
        this.sessionIds = sessionIds;
    }

    @Override
    public String description() {
        return "state-derived seat drift (Capacity - BOOKED - ATTENDED vs SeatsAvailable)";
    }

    @Override
    public List<SeatDrift> findDrift() {
        List<SeatDrift> out = new ArrayList<>();
        for (int id : sessionIds.get()) {
            ExamSessionState s = target.session(id);
            int active = 0;
            for (BookingState b : target.bookings(id)) {
                if ("BOOKED".equals(b.status()) || "ATTENDED".equals(b.status())) {
                    active++;
                }
            }
            int drift = (s.capacity() - active) - s.seatsAvailable();
            if (drift != 0) {
                out.add(new SeatDrift(id, s.capacity(), s.seatsAvailable(), active, drift));
            }
        }
        return out;
    }
}
