package com.northwind.assessment.booking.model;

/**
 * One scored attempt (Result row joined to its session).
 */
public class ExamAttempt {

    private int resultId;
    private int examId;
    private java.util.Date sessionDate;
    private int score;
    private boolean passed;

    public int getResultId() {
        return resultId;
    }

    public void setResultId(int resultId) {
        this.resultId = resultId;
    }

    public int getExamId() {
        return examId;
    }

    public void setExamId(int examId) {
        this.examId = examId;
    }

    public java.util.Date getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(java.util.Date sessionDate) {
        this.sessionDate = sessionDate;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }
}
