package com.northwind.assessment.scheduling.outbox;

/** Outbox event types (MBA-3823). The names are stored in {@code outbox_event.event_type}. */
public enum OutboxEventType {
    BookingCreated("BOOK"),
    BookingWaitlisted("WAITLIST"),
    BookingCancelled("CANCEL"),
    BookingRescheduled("RESCHEDULE"),
    AttendanceRecorded("ATTEND"),
    CapacityChanged("CAPACITY");

    private final String auditAction;

    OutboxEventType(String auditAction) {
        this.auditAction = auditAction;
    }

    /** {@code AuditLog.Action} the relay writes for this event (the legacy procs use BOOK / CANCEL / CAPACITY). */
    public String auditAction() {
        return auditAction;
    }
}
