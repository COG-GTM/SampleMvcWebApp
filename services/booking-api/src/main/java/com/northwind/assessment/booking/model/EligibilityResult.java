package com.northwind.assessment.booking.model;

public class EligibilityResult {

    private boolean eligible;
    private String reason;
    private String earliestRetakeDate;

    public EligibilityResult() {
    }

    public EligibilityResult(boolean eligible, String reason, String earliestRetakeDate) {
        this.eligible = eligible;
        this.reason = reason;
        this.earliestRetakeDate = earliestRetakeDate;
    }

    public boolean isEligible() {
        return eligible;
    }

    public void setEligible(boolean eligible) {
        this.eligible = eligible;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getEarliestRetakeDate() {
        return earliestRetakeDate;
    }

    public void setEarliestRetakeDate(String earliestRetakeDate) {
        this.earliestRetakeDate = earliestRetakeDate;
    }
}
