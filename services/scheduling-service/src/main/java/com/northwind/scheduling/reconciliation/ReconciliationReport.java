package com.northwind.scheduling.reconciliation;

import java.nio.file.Path;

/** Markdown for reports/reconciliation-&lt;timestamp&gt;.md (MBA-3823). */
final class ReconciliationReport {

    private static final int MAX_ROWS = 200;

    private ReconciliationReport() {
    }

    static String markdown(Reconciliation r, Path seatDriftSql) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Reconciliation: Scheduling vs AssessmentDelivery\n\n");
        sb.append("Generated ").append(r.generatedAt()).append(" by scheduling-service `reconcile` (MBA-3823).\n\n");
        sb.append("| check | result |\n|---|---|\n");
        sb.append("| sessions compared | ").append(r.sessionsCompared()).append(" |\n");
        sb.append("| bookings compared | ").append(r.bookingsCompared()).append(" |\n");
        sb.append("| session field mismatches | ").append(r.sessionMismatches().size()).append(" |\n");
        sb.append("| booking mismatches | ").append(r.bookingMismatches().size()).append(" |\n");
        sb.append("| **total mismatches** | **").append(r.mismatches()).append("** |\n");
        sb.append("| `db/checks/seat-drift.sql` rows | ").append(r.seatDrift().size()).append(" |\n\n");
        sb.append("Per session: Capacity, SeatsAvailable (Scheduling derives Capacity - (BOOKED + ATTENDED), floored at 0; ")
                .append("legacy is the stored counter), BOOKED / ATTENDED / WAITLISTED counts and accommodation seats used ")
                .append("(BOOKED + ATTENDED with ET25/ET50). Per booking: session, status and accommodation code, keyed on the legacy BookingId ")
                .append("(`booking.legacy_booking_id`).\n\n");

        sb.append("## Session mismatches\n\n");
        if (r.sessionMismatches().isEmpty()) {
            sb.append("None.\n\n");
        } else {
            sb.append("| SessionId | field | Scheduling | AssessmentDelivery |\n|---|---|---|---|\n");
            r.sessionMismatches().stream().limit(MAX_ROWS).forEach(m -> sb.append("| ").append(m.sessionId()).append(" | ")
                    .append(m.field()).append(" | ").append(n(m.scheduling())).append(" | ").append(n(m.legacy())).append(" |\n"));
            more(sb, r.sessionMismatches().size());
        }

        sb.append("## Booking mismatches\n\n");
        if (r.bookingMismatches().isEmpty()) {
            sb.append("None.\n\n");
        } else {
            sb.append("| BookingId | Scheduling session | Scheduling status | Scheduling accommodation | legacy session | legacy status | legacy accommodation |\n|---|---|---|---|---|---|---|\n");
            r.bookingMismatches().stream().limit(MAX_ROWS).forEach(m -> sb.append("| ").append(m.bookingId()).append(" | ")
                    .append(n(m.schedulingSessionId())).append(" | ").append(n(m.schedulingStatus())).append(" | ").append(n(m.schedulingAccommodation())).append(" | ")
                    .append(n(m.legacySessionId())).append(" | ").append(n(m.legacyStatus())).append(" | ").append(n(m.legacyAccommodation())).append(" |\n"));
            more(sb, r.bookingMismatches().size());
        }

        sb.append("## `db/checks/seat-drift.sql`\n\n");
        sb.append("Source: `db/checks/").append(seatDriftSql.getFileName()).append("`. ");
        if (r.seatDrift().isEmpty()) {
            sb.append("0 rows (no drift).\n");
        } else {
            sb.append(r.seatDrift().size()).append(" rows:\n\n");
            sb.append("| SessionId | ExamCode | CenterCode | StartsAt | Capacity | SeatsAvailable | ActiveBookings | Drift |\n")
                    .append("|---|---|---|---|---|---|---|---|\n");
            r.seatDrift().stream().limit(MAX_ROWS).forEach(d -> sb.append("| ").append(d.sessionId()).append(" | ")
                    .append(d.examCode()).append(" | ").append(d.centerCode()).append(" | ").append(d.startsAt())
                    .append(" | ").append(d.capacity()).append(" | ").append(d.seatsAvailable()).append(" | ")
                    .append(d.activeBookings()).append(" | ").append(d.drift()).append(" |\n"));
            more(sb, r.seatDrift().size());
        }
        return sb.toString();
    }

    private static void more(StringBuilder sb, int size) {
        sb.append(size > MAX_ROWS ? "\n... " + (size - MAX_ROWS) + " more rows\n\n" : "\n");
    }

    private static String n(Object o) {
        return o == null ? "(missing)" : o.toString();
    }
}
