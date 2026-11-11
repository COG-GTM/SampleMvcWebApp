package com.northwind.scheduling.characterization.spi;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Row-level writes that bypass the scheduling operations ({@link Capability#RAW_STORAGE}). Legacy writers that
 * do this: 2013 bulk imports (trigger 032), booking-api partner path (HB-01), ad-hoc admin UPDATEs (trigger 033).
 * A Phase 1 target may implement this against its own tables, or not support it.
 */
public interface RawStorageAccess {

    /** Single-row {@code INSERT INTO Booking}; {@code channel == null} uses the column default. Returns BookingId. */
    int insertBooking(int sessionId, int candidateId, String status, String channel, String accommodationCode);

    /** One multi-row INSERT statement (all rows in the same statement). Returns the BookingIds. */
    List<Integer> insertBookings(List<int[]> sessionAndCandidate, String status, String channel);

    void updateBookingStatus(int bookingId, String status);

    void deleteBooking(int bookingId);

    void setBookedAt(int bookingId, LocalDateTime bookedAt);

    /** booking-api {@code SessionDao.decrementSeatsAvailable} style counter update (delta may be negative). */
    void adjustSeatsAvailable(int sessionId, int delta);

    /** Direct {@code UPDATE ExamSession SET Capacity = ?} (not via the proc). */
    void updateCapacity(int sessionId, int capacity);

    /** One UPDATE statement changing several sessions' Capacity. */
    void updateCapacities(Map<Integer, Integer> capacityBySession);

    /** {@code UPDATE ExamSession SET Status = ?}. */
    void setSessionStatus(int sessionId, String status);

    /** {@code UPDATE ExamSession SET LastModified = ?}. */
    void setLastModified(int sessionId, LocalDateTime value);

    /**
     * booking-api partner booking ({@code BookingService.book} statements: read SeatsAvailable, INSERT Booking with
     * Channel=PARTNER, decrement or waitlist, audit). Returns BookingId.
     */
    int partnerBook(int sessionId, int candidateId, String accommodationCode);
}
