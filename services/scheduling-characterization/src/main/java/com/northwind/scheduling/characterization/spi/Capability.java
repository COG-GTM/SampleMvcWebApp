package com.northwind.scheduling.characterization.spi;

/** Optional target features. Scenarios that need one are skipped (JUnit assumption) on targets without it. */
public enum Capability {
    /**
     * Row-level writes that bypass the operations (direct INSERT/UPDATE/DELETE as bulk imports, booking-api,
     * results-service or an ad-hoc admin script do). Needed by the trigger-032/033 storage scenarios.
     * See {@link RawStorageAccess}.
     */
    RAW_STORAGE,
    /**
     * Legacy SQL Server details: {@link SchedulingError#nativeNumber()} / {@link SchedulingError#nativeState()} are the
     * SQL message number / RAISERROR state, and a missing audit actor defaults to {@code SUSER_SNAME()} (the login).
     */
    LEGACY_SQL_SEMANTICS,
    /** The target provisioned its own empty/seeded store for this run (not a pre-existing database). */
    FRESH_DATABASE
}
