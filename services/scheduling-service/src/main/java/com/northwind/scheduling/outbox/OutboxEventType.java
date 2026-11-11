package com.northwind.scheduling.outbox;

/** Outbox event types (MBA-3823); ck_outbox_event_type in V2__outbox.sql lists the same names. */
public enum OutboxEventType {
    BookingCreated("Booking", "BOOK"),
    BookingCancelled("Booking", "CANCEL"),
    BookingRescheduled("Booking", "RESCHEDULE"),
    BookingWaitlisted("Booking", "WAITLIST"),
    AttendanceRecorded("Booking", "ATTENDED"),
    CapacityChanged("ExamSession", "CAPACITY_CHANGE"),
    /** after a snapshot import: SeatsAvailable for every session (scheduling-service is the single seat writer) */
    InventoryBaselined("ExamSession", "SEAT_BASELINE");

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
