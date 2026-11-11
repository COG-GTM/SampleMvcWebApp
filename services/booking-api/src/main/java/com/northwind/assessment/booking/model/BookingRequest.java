package com.northwind.assessment.booking.model;

/**
 * POST /api/v1/bookings body.
 */
public class BookingRequest {

    private Integer sessionId;
    private String candidateNumber;
    private String accommodationCode;
    private String externalRef;

    public Integer getSessionId() {
        return sessionId;
    }

    public void setSessionId(Integer sessionId) {
        this.sessionId = sessionId;
    }

    public String getCandidateNumber() {
        return candidateNumber;
    }

    public void setCandidateNumber(String candidateNumber) {
        this.candidateNumber = candidateNumber;
    }

    public String getAccommodationCode() {
        return accommodationCode;
    }

    public void setAccommodationCode(String accommodationCode) {
        this.accommodationCode = accommodationCode;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }
}
