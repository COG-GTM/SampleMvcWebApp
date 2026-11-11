package com.northwind.assessment.scheduling.legacy;

import java.util.List;

/**
 * Where scheduling-service gets data owned by other systems. Today: a read-only JDBC login to
 * AssessmentDelivery ({@link JdbcLegacyReferenceSource}). Later (M4/M5): events. Callers never see
 * SQL, so swapping the implementation does not touch the importer or the domain.
 */
public interface LegacyReferenceSource {

    List<LegacyRows.TestCenter> testCenters();

    List<LegacyRows.Room> rooms();

    List<LegacyRows.Exam> exams();

    List<LegacyRows.Candidate> candidates();

    List<LegacyRows.Result> results();

    /** Sessions of one center, or of every center when {@code testCenterId} is null. */
    List<LegacyRows.ExamSession> sessions(Integer testCenterId);

    List<LegacyRows.Booking> bookings(Integer testCenterId);

    List<LegacyRows.Waitlist> waitlist(Integer testCenterId);
}
