package com.northwind.ad.results.model;

/** POST /api/v1/results body. scoredAt is yyyy-MM-dd'T'HH:mm:ss, defaults to now. */
public class ResultRequest {

    private Integer bookingId;
    private Integer score;
    private String scoredAt;

    public Integer getBookingId() {
        return bookingId;
    }

    public void setBookingId(Integer bookingId) {
        this.bookingId = bookingId;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getScoredAt() {
        return scoredAt;
    }

    public void setScoredAt(String scoredAt) {
        this.scoredAt = scoredAt;
    }
}
