package com.northwind.scheduling.characterization.spi;

import com.northwind.scheduling.characterization.spi.Model.SessionSpec;

/**
 * Creates isolated fixture rows so scenarios never depend on (wall-clock sensitive) seed data.
 * Exam / Candidate / TestCenter / Room are reference data: in Phase 1 they are replicated into the new service.
 */
public interface Fixtures {

    /** New published exam with a unique code; returns ExamId. */
    int newExam();

    /** New candidate with a unique candidate number; returns CandidateId. */
    int newCandidate();

    /** New exam session (in a fixture test centre/room, starting in the future unless given); returns SessionId. */
    int newSession(SessionSpec spec);
}
