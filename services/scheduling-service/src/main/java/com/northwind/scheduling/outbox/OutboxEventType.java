package com.northwind.scheduling.outbox;

/** Outbox event types (MBA-3823); ck_outbox_event_type (V2__outbox.sql, V3__waitlist_offers.sql) lists the same names. */
public enum OutboxEventType {
    BookingCreated("Booking", "BOOK"),
    BookingCancelled("Booking", "CANCEL"),
    BookingRescheduled("Booking", "RESCHEDULE"),
    BookingWaitlisted("Booking", "WAITLIST"),
    AttendanceRecorded("Booking", "ATTENDED"),
    CapacityChanged("ExamSession", "CAPACITY_CHANGE"),
    /** after a snapshot import: SeatsAvailable for every session (scheduling-service is the single seat writer) */
    InventoryBaselined("ExamSession", "SEAT_BASELINE"),
    /** MBA-3829 (ADR 0004): a held seat offered to a waitlisted booking; legacy rows unchanged (still WAITLISTED) */
    SeatOffered("Booking", "WAITLIST_OFFER"),
    /** offer accepted: booking BOOKED, waitlist row removed, later positions shifted up */
    SeatOfferAccepted("Booking", "WAITLIST_OFFER_ACCEPT"),
    /** offer declined: booking CANCELLED, waitlist row removed, later positions shifted up */
    SeatOfferDeclined("Booking", "WAITLIST_OFFER_DECLINE"),
    /** offer expired (scheduling.waitlist.offer-ttl): booking CANCELLED, waitlist row removed, positions shifted */
    SeatOfferExpired("Booking", "WAITLIST_OFFER_EXPIRE"),
    /** offer withdrawn (candidate cancelled/rescheduled, or capacity reduced); candidate keeps the waitlist place */
    SeatOfferWithdrawn("Booking", "WAITLIST_OFFER_WITHDRAW");

    private final String aggregateType;
    private final String auditAction;

    OutboxEventType(String aggregateType, String auditAction) {
        this.aggregateType = aggregateType;
        this.auditAction = auditAction;
    }

    public String aggregateType() {
        return aggregateType;
    }

    /** dbo.AuditLog.Action the relay writes for this event */
    public String auditAction() {
        return auditAction;
    }
}
