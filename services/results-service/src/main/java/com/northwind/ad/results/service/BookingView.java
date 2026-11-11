package com.northwind.ad.results.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The fields results-service needs from scheduling-service's Booking schema. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingView {

    private int bookingId;
    private int sessionId;
    private int candidateId;
    private String candidateNumber;
    private Integer examId;
    private String sessionStartsAt;
    private String status;

    public int getBookingId() { return bookingId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }
    public int getSessionId() { return sessionId; }
    public void setSessionId(int sessionId) { this.sessionId = sessionId; }
    public int getCandidateId() { return candidateId; }
    public void setCandidateId(int candidateId) { this.candidateId = candidateId; }
    public String getCandidateNumber() { return candidateNumber; }
    public void setCandidateNumber(String candidateNumber) { this.candidateNumber = candidateNumber; }
    public Integer getExamId() { return examId; }
    public void setExamId(Integer examId) { this.examId = examId; }
    public String getSessionStartsAt() { return sessionStartsAt; }
    public void setSessionStartsAt(String sessionStartsAt) { this.sessionStartsAt = sessionStartsAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
