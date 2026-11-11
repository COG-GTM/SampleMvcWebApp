package com.northwind.assessment.scheduling.outbox;

/** Outbox event types (MBA-3823). The names are stored in {@code outbox_event.event_type}. */
public enum OutboxEventType {
    BookingCreated("BOOK"),
    BookingWaitlisted("WAITLIST"),
    BookingCancelled("CANCEL"),
    BookingRescheduled("RESCHEDULE"),
    AttendanceRecorded("ATTEND"),
    CapacityChanged("CAPACITY"),
    /** MBA-3829 (ADR 0003): a waitlisted booking was offered a freed seat; the seat is held while PENDING. */
    SeatOffered("OFFER"),
    SeatOfferAccepted("OFFER_ACCEPT"),
    /** The candidate declined: the waitlisted booking is cancelled and later positions move up. */
    SeatOfferDeclined("OFFER_DECLINE"),
    /** Not accepted before expires_at: like a decline, recorded by the expiry job. */
    SeatOfferExpired("OFFER_EXPIRE");

    private final String auditAction;

    OutboxEventType(String auditAction) {
        this.auditAction = auditAction;
    }

    /** {@code AuditLog.Action} the relay writes for this event (the legacy procs use BOOK / CANCEL / CAPACITY). */
    public String auditAction() {
        return auditAction;
    }
}
