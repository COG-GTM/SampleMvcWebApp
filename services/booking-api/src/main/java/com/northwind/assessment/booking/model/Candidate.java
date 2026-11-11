package com.northwind.assessment.booking.model;

public class Candidate {

    private int candidateId;
    private String candidateNumber;
    private String firstName;
    private String lastName;
    private String defaultAccommodationCode;

    public int getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(int candidateId) {
        this.candidateId = candidateId;
    }

    public String getCandidateNumber() {
        return candidateNumber;
    }

    public void setCandidateNumber(String candidateNumber) {
        this.candidateNumber = candidateNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getDefaultAccommodationCode() {
        return defaultAccommodationCode;
    }

    public void setDefaultAccommodationCode(String defaultAccommodationCode) {
        this.defaultAccommodationCode = defaultAccommodationCode;
    }
}
