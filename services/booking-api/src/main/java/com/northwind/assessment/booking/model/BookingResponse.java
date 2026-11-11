package com.northwind.assessment.booking.model;

public class BookingResponse {

    private int bookingId;
    private String status;

    public BookingResponse() {
    }

    public BookingResponse(int bookingId, String status) {
        this.bookingId = bookingId;
        this.status = status;
    }

    public int getBookingId() {
        return bookingId;
    }

    public void setBookingId(int bookingId) {
        this.bookingId = bookingId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
