package com.northwind.scheduling.characterization.spi;

import java.util.regex.Pattern;

/**
 * Named error conditions of the legacy scheduling procs.
 *
 * <p>The proc headers document numeric codes 50001-50006, but every proc raises an ad-hoc
 * {@code RAISERROR(msg, 16, n)}: SQL Server reports message number 50000 and the "code" is only in
 * the error state (HB-14). {@link #legacyCode()} keeps the documented number (50000 + state) so the
 * scenarios can assert it on every target.
 */
public enum ErrorCondition {
    /** 020 state 1: session not found or Status &lt;&gt; 'OPEN'. */
    SESSION_NOT_OPEN(50001, "Session (-?\\d+|\\(null\\)) is not open for booking\\."),
    /** 020 state 2: candidate already holds a BOOKED or WAITLISTED booking on the session. */
    DUPLICATE_ACTIVE_BOOKING(50002, "Candidate (-?\\d+|\\(null\\)) already has an active booking on session (-?\\d+|\\(null\\))\\."),
    /** 020 state 3: ET25/ET50 booking and the accommodation pool is used up. */
    NO_ACCOMMODATION_SEAT(50003, "No accommodation seat available on session (-?\\d+|\\(null\\))\\."),
    /** 021 state 4: booking not found or not BOOKED/WAITLISTED. */
    BOOKING_NOT_CANCELLABLE(50004, "Booking (-?\\d+|\\(null\\)) cannot be cancelled\\."),
    /** 022 state 5: session not found, capacity &lt; 0 or capacity &lt; AccommodationCapacity. */
    INVALID_CAPACITY(50005, "Invalid capacity (-?\\d+|\\(null\\)) for session (-?\\d+|\\(null\\))\\."),
    /** 023 state 6: booking/target session not found or target is a different exam. */
    RESCHEDULE_NOT_ALLOWED(50006, "Booking (-?\\d+|\\(null\\)) cannot be moved to session (-?\\d+|\\(null\\))\\."),
    /** Anything else (FK violation, deadlock, ...). Legacy re-raises these as 50000/16 too (HB-14). */
    SYSTEM_ERROR(50000, ".*");

    private final int legacyCode;
    private final Pattern message;

    ErrorCondition(int legacyCode, String messageRegex) {
        this.legacyCode = legacyCode;
        this.message = Pattern.compile(messageRegex, Pattern.DOTALL);
    }

    /** Documented legacy code: 50000 + RAISERROR state for proc errors, 50000 for system errors. */
    public int legacyCode() {
        return legacyCode;
    }

    public boolean matchesMessage(String text) {
        return text != null && message.matcher(text).matches();
    }

    /**
     * Maps a legacy (state, message) pair. The state alone is ambiguous (e.g. Msg 266 also has state 2),
     * so the proc's message template must match too, as the portal's SqlErrorHelper does by text.
     */
    public static ErrorCondition fromLegacy(int state, String messageText) {
        for (ErrorCondition c : values()) {
            if (c != SYSTEM_ERROR && c.legacyCode - 50000 == state && c.matchesMessage(messageText)) {
                return c;
            }
        }
        return SYSTEM_ERROR;
    }
}
