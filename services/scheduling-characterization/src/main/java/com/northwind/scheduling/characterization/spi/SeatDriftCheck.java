package com.northwind.scheduling.characterization.spi;

import java.util.List;

import com.northwind.scheduling.characterization.spi.Model.SeatDrift;

/**
 * Seat-drift invariant: sessions whose SeatsAvailable counter does not equal Capacity - (BOOKED + ATTENDED).
 * Mirrors db/checks/seat-drift.sql. An empty list means no drift.
 */
public interface SeatDriftCheck {

    String description();

    List<SeatDrift> findDrift();
}
