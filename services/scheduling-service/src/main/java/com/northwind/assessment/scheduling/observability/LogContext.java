package com.northwind.assessment.scheduling.observability;

import java.util.List;
import org.slf4j.MDC;

/**
 * MDC keys written to every JSON log line (see logback-spring.xml). Only these keys are emitted; candidate
 * identity is limited to {@code candidateNumber} - never names, e-mail addresses or dates of birth.
 */
public final class LogContext {

    public static final String TRACE_ID = "traceId";
    public static final String CENTER_CODE = "centerCode";
    public static final String SESSION_ID = "sessionId";
    public static final String BOOKING_ID = "bookingId";
    public static final String CANDIDATE_NUMBER = "candidateNumber";

    /** Keys a booking operation may set; reset at the start of each operation. */
    static final List<String> OPERATION_KEYS = List.of(CENTER_CODE, SESSION_ID, BOOKING_ID, CANDIDATE_NUMBER);

    public static final String UNKNOWN_CENTER = "unknown";

    private LogContext() {
    }

    public static void centerCode(String centerCode) {
        put(CENTER_CODE, centerCode);
    }

    public static void sessionId(Number sessionId) {
        put(SESSION_ID, sessionId == null ? null : sessionId.toString());
    }

    public static void bookingId(Number bookingId) {
        put(BOOKING_ID, bookingId == null ? null : bookingId.toString());
    }

    public static void candidateNumber(String candidateNumber) {
        put(CANDIDATE_NUMBER, candidateNumber);
    }

    /** The resolved center code of the current operation, or {@value #UNKNOWN_CENTER}. */
    public static String centerCodeOrUnknown() {
        String c = MDC.get(CENTER_CODE);
        return c == null || c.isBlank() ? UNKNOWN_CENTER : c;
    }

    static void clearOperation() {
        OPERATION_KEYS.forEach(MDC::remove);
    }

    static void clearAll() {
        clearOperation();
        MDC.remove(TRACE_ID);
    }

    private static void put(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
        } else {
            MDC.put(key, value.trim());
        }
    }
}
