package com.northwind.ad.results.service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/** What {@link ResultIngestService#ingest} did with one submitted score. */
public class IngestOutcome {

    public enum Status {
        /** new Result row inserted, booking marked ATTENDED */
        CREATED,
        /** identical result already stored; nothing changed */
        DUPLICATE,
        /** a different result is stored for the booking; nothing changed, DUPLICATE_REJECTED audited */
        CONFLICT,
        /** no result stored and inserting was not allowed (replayed score file) */
        NOT_IMPORTED
    }

    private final Status status;
    private final int bookingId;
    private final Integer resultId;
    private final int submittedScore;
    private final Date submittedScoredAt;
    private final Integer storedScore;
    private final Date storedScoredAt;
    private final Boolean passed;

    IngestOutcome(Status status, int bookingId, Integer resultId, int submittedScore, Date submittedScoredAt,
                  Integer storedScore, Date storedScoredAt, Boolean passed) {
        this.status = status;
        this.bookingId = bookingId;
        this.resultId = resultId;
        this.submittedScore = submittedScore;
        this.submittedScoredAt = submittedScoredAt;
        this.storedScore = storedScore;
        this.storedScoredAt = storedScoredAt;
        this.passed = passed;
    }

    public Status getStatus() {
        return status;
    }

    public int getBookingId() {
        return bookingId;
    }

    public Integer getResultId() {
        return resultId;
    }

    public int getSubmittedScore() {
        return submittedScore;
    }

    public Date getSubmittedScoredAt() {
        return submittedScoredAt;
    }

    public Integer getStoredScore() {
        return storedScore;
    }

    public Date getStoredScoredAt() {
        return storedScoredAt;
    }

    public Boolean getPassed() {
        return passed;
    }

    /** REST body for CREATED and DUPLICATE. */
    public Map<String, Object> toBody() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("status", status.name());
        out.put("resultId", resultId);
        out.put("bookingId", bookingId);
        out.put("score", status == Status.CREATED ? Integer.valueOf(submittedScore) : storedScore);
        out.put("passed", passed);
        return out;
    }

    /** Extra 409 body fields for CONFLICT: both scores side by side. */
    public Map<String, Object> conflictDetails() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("bookingId", bookingId);
        out.put("resultId", resultId);
        out.put("storedScore", storedScore);
        out.put("submittedScore", submittedScore);
        out.put("storedScoredAt", ResultIngestService.format(storedScoredAt));
        out.put("submittedScoredAt", ResultIngestService.format(submittedScoredAt));
        return out;
    }
}
