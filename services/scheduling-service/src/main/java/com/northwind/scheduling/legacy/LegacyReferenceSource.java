package com.northwind.scheduling.legacy;

import java.util.List;

/** Read-only view of the legacy AssessmentDelivery tables the importer copies. */
public interface LegacyReferenceSource {
    List<LegacyRows.Center> centers();

    List<LegacyRows.Room> rooms();

    List<LegacyRows.Exam> exams();

    List<LegacyRows.Candidate> candidates();

    List<LegacyRows.Result> results();

    List<LegacyRows.Session> sessions();

    List<LegacyRows.Booking> bookings();

    List<LegacyRows.Waitlist> waitlist();

    /** Throws when the source is unreachable (readiness probe, observability/LegacySourceHealthIndicator). */
    default void ping() {
    }
}
