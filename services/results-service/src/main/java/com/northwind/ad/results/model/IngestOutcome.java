package com.northwind.ad.results.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What happened to one submitted result: CREATED (new row), DUPLICATE (identical result already
 * stored, nothing written) or CONFLICT (a different result is already stored, nothing written).
 */
public class IngestOutcome {

    public enum Status { CREATED, DUPLICATE, CONFLICT }

    private final Status status;
    private final int resultId;
    private final int bookingId;
    private final int score;
    private final boolean passed;
    private final Integer submittedScore;
    private final Date storedScoredAt;
    private final Date submittedScoredAt;

    private IngestOutcome(Status status, int resultId, int bookingId, int score, boolean passed,
                          Integer submittedScore, Date storedScoredAt, Date submittedScoredAt) {
        this.status = status;
        this.resultId = resultId;
        this.bookingId = bookingId;
        this.score = score;
        this.passed = passed;
        this.submittedScore = submittedScore;
        this.storedScoredAt = storedScoredAt;
        this.submittedScoredAt = submittedScoredAt;
    }

    public static IngestOutcome created(int resultId, int bookingId, int score, boolean passed) {
        return new IngestOutcome(Status.CREATED, resultId, bookingId, score, passed, null, null, null);
    }

    public static IngestOutcome duplicate(StoredResult stored) {
        return new IngestOutcome(Status.DUPLICATE, stored.getResultId(), stored.getBookingId(), stored.getScore(),
                stored.isPassed(), null, stored.getScoredAt(), null);
    }

    /** @param submittedScoredAt null when the caller did not send one */
    public static IngestOutcome conflict(StoredResult stored, int submittedScore, Date submittedScoredAt) {
        return new IngestOutcome(Status.CONFLICT, stored.getResultId(), stored.getBookingId(), stored.getScore(),
                stored.isPassed(), submittedScore, stored.getScoredAt(), submittedScoredAt);
    }

    public Status getStatus() {
        return status;
    }

    public int getResultId() {
        return resultId;
    }

    public int getBookingId() {
        return bookingId;
    }

    /** The stored score for DUPLICATE / CONFLICT, the inserted score for CREATED. */
    public int getScore() {
        return score;
    }

    public boolean isPassed() {
        return passed;
    }

    public Integer getSubmittedScore() {
        return submittedScore;
    }

    public Date getStoredScoredAt() {
        return storedScoredAt;
    }

    public Date getSubmittedScoredAt() {
        return submittedScoredAt;
    }

    /** Response body of POST /api/v1/results for CREATED (201) and DUPLICATE (200). */
    public Map<String, Object> toBody() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("status", status.name());
        out.put("resultId", resultId);
        out.put("bookingId", bookingId);
        out.put("score", score);
        out.put("passed", passed);
        return out;
    }

    /** Extra fields of the 409 DUPLICATE_RESULT body. */
    public Map<String, Object> conflictDetails() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("bookingId", bookingId);
        out.put("resultId", resultId);
        out.put("storedScore", score);
        out.put("submittedScore", submittedScore);
        out.put("storedScoredAt", format(storedScoredAt));
        out.put("submittedScoredAt", format(submittedScoredAt));
        return out;
    }

    public String conflictMessage() {
        return "A different result already exists for booking " + bookingId + ": stored score " + score
                + ", submitted score " + submittedScore;
    }

    public static String format(Date d) {
        return d == null ? null : new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(d);
    }
}
