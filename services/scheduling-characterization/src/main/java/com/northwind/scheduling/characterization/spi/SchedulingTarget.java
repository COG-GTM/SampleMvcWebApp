package com.northwind.scheduling.characterization.spi;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.northwind.scheduling.characterization.spi.Model.AuditEntry;
import com.northwind.scheduling.characterization.spi.Model.BookingState;
import com.northwind.scheduling.characterization.spi.Model.ExamSessionState;
import com.northwind.scheduling.characterization.spi.Model.WaitlistEntry;

/**
 * The system under characterization. Every scenario is written against this interface only, so the same
 * scenarios run against the legacy procs ({@code -Dtarget=legacy}) and, in Phase 1, the new scheduling-service.
 *
 * <p>Implementations must be thread-safe: the concurrency scenarios call operations from many threads at once.
 * Operations never throw for business errors; they return an {@link Outcome} carrying a {@link SchedulingError}.
 */
public interface SchedulingTarget extends AutoCloseable {

    /** Short name used by {@code -Dtarget=...}. */
    String name();

    Set<Capability> capabilities();

    default boolean supports(Capability c) {
        return capabilities().contains(c);
    }

    // ---- operations (legacy: db/procs/020..023) -------------------------------------------------

    /** Portal booking. Legacy: {@code usp_BookSeat}. {@code accommodationCode} may be null. */
    Outcome book(int sessionId, int candidateId, String accommodationCode);

    /** Legacy: {@code usp_CancelBooking}. {@code reason}/{@code actor} may be null. */
    Outcome cancel(int bookingId, String reason, String actor);

    /** Legacy: {@code usp_ChangeCapacity} (+ the demotion in {@code trg_ExamSession_Update}). */
    Outcome changeCapacity(int sessionId, int newCapacity, String actor);

    /** Legacy: {@code usp_RescheduleBooking}. Returns the new booking id and its status. */
    Outcome reschedule(int bookingId, int newSessionId);

    /**
     * Results ingest marking a booking ATTENDED. Legacy: the exact statement results-service issues
     * ({@code ResultIngestService}: {@code UPDATE Booking SET Status='ATTENDED' WHERE BookingId=? AND Status='BOOKED'}).
     */
    void markAttended(int bookingId);

    /**
     * Seat reconciler. Legacy: {@code CapacitySyncService.runSync} (capacity-sync), restricted to the given
     * sessions so it does not rewrite unrelated rows. Returns the number of sessions updated.
     */
    int reconcileSeats(Collection<Integer> sessionIds);

    // ---- state reads -----------------------------------------------------------------------------

    ExamSessionState session(int sessionId);

    BookingState booking(int bookingId);

    /** All bookings of a session ordered by BookingId. */
    List<BookingState> bookings(int sessionId);

    /** Waitlist rows of a session ordered by Position, then insertion order. */
    List<WaitlistEntry> waitlist(int sessionId);

    /** Audit rows for one entity ordered by insertion. */
    List<AuditEntry> audit(String entityName, int entityId);

    // ---- fixtures, invariant, optional raw access -----------------------------------------------

    Fixtures fixtures();

    /** Seat-drift invariant over the whole target (legacy: runs db/checks/seat-drift.sql). */
    SeatDriftCheck seatDriftCheck();

    /** Present only when {@link Capability#RAW_STORAGE} is supported. */
    default Optional<RawStorageAccess> rawStorage() {
        return Optional.empty();
    }

    @Override
    default void close() {
    }
}
