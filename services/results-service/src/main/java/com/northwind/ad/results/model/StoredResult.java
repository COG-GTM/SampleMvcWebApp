package com.northwind.ad.results.model;

import java.util.Date;

/** The Result row already stored for a booking (UQ_Result_Booking allows one). */
public class StoredResult {

    private final int resultId;
    private final int bookingId;
    private final int score;
    private final boolean passed;
    private final Date scoredAt;

    public StoredResult(int resultId, int bookingId, int score, boolean passed, Date scoredAt) {
        this.resultId = resultId;
        this.bookingId = bookingId;
        this.score = score;
        this.passed = passed;
        this.scoredAt = scoredAt;
    }

    public int getResultId() {
        return resultId;
    }

    public int getBookingId() {
        return bookingId;
    }

    public int getScore() {
        return score;
    }

    public boolean isPassed() {
        return passed;
    }

    public Date getScoredAt() {
        return scoredAt;
    }
}
