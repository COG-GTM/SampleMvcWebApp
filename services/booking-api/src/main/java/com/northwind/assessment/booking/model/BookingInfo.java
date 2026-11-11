package com.northwind.assessment.booking.model;

public class BookingInfo {

    private int bookingId;
    private int sessionId;
    private String candidateNumber;
    private String examCode;
    private String sessionStartsAt;
    private String status;
    private String accommodationCode;
    private String channel;
    private String externalRef;
    private String bookedAt;
    private String cancelledAt;
    private String cancelReason;

    public int getBookingId() {
        return bookingId;
    }

    public void setBookingId(int bookingId) {
        this.bookingId = bookingId;
    }

    public int getSessionId() {
        return sessionId;
    }

    public void setSessionId(int sessionId) {
        this.sessionId = sessionId;
    }

    public String getCandidateNumber() {
        return candidateNumber;
    }

    public void setCandidateNumber(String candidateNumber) {
        this.candidateNumber = candidateNumber;
    }

    public String getExamCode() {
        return examCode;
    }

    public void setExamCode(String examCode) {
        this.examCode = examCode;
    }

    public String getSessionStartsAt() {
        return sessionStartsAt;
    }

    public void setSessionStartsAt(String sessionStartsAt) {
        this.sessionStartsAt = sessionStartsAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAccommodationCode() {
        return accommodationCode;
    }

    public void setAccommodationCode(String accommodationCode) {
        this.accommodationCode = accommodationCode;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    public String getBookedAt() {
        return bookedAt;
    }

    public void setBookedAt(String bookedAt) {
        this.bookedAt = bookedAt;
    }

    public String getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(String cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }
}
