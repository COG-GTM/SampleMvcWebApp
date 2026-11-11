package com.northwind.scheduling.characterization.spi;

/**
 * Result of book / cancel / changeCapacity / reschedule.
 *
 * @param bookingId booking id created by book/reschedule (null otherwise or on error)
 * @param status    BOOKED / WAITLISTED for book/reschedule, null otherwise or on error
 * @param error     null on success
 */
public record Outcome(Integer bookingId, String status, SchedulingError error) {

    public static Outcome ok() {
        return new Outcome(null, null, null);
    }

    public static Outcome booked(int bookingId, String status) {
        return new Outcome(bookingId, status, null);
    }

    public static Outcome failed(SchedulingError error) {
        return new Outcome(null, null, error);
    }

    public boolean isSuccess() {
        return error == null;
    }

    public ErrorCondition condition() {
        return error == null ? null : error.condition();
    }

    public Integer errorCode() {
        return error == null ? null : error.code();
    }

    @Override
    public String toString() {
        return error == null ? "Outcome[" + bookingId + "," + status + "]"
                : "Outcome[error " + error.code() + " " + error.condition() + ": " + error.message() + "]";
    }
}
