package com.northwind.assessment.scheduling.service;

/** Response of POST /api/v2/bookings and of a reschedule (the new booking). */
public record BookingResult(long bookingId, String status, int sessionId, String candidateNumber, String accommodationCode,
                            String channel, Integer waitlistPosition, int seatsAvailable) {
}
