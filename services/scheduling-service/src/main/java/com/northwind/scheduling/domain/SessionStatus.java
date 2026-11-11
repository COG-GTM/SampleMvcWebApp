package com.northwind.scheduling.domain;

/** ExamSession.Status values (legacy CK_ExamSession_Status). Only OPEN accepts bookings (usp_BookSeat state 1). */
public enum SessionStatus {
    OPEN, CLOSED, DELIVERED, CANCELLED;

    public boolean acceptsBookings() {
        return this == OPEN;
    }
}
