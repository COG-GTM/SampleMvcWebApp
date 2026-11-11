package com.northwind.assessment.capacity;

/** Outcome of one re-sync run, returned by GET /sync/last. */
public class SyncRun {

    private String status = "NEVER_RUN";
    private String trigger;
    private String startedAt;
    private String finishedAt;
    private int sessionsChecked;
    private int sessionsUpdated;
    private String message;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(String finishedAt) {
        this.finishedAt = finishedAt;
    }

    public int getSessionsChecked() {
        return sessionsChecked;
    }

    public void setSessionsChecked(int sessionsChecked) {
        this.sessionsChecked = sessionsChecked;
    }

    public int getSessionsUpdated() {
        return sessionsUpdated;
    }

    public void setSessionsUpdated(int sessionsUpdated) {
        this.sessionsUpdated = sessionsUpdated;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
