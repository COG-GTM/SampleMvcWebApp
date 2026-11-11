package com.northwind.scheduling.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.northwind.scheduling.domain.CapacityImpact;
import com.northwind.scheduling.notification.LoggingNotificationAdapter;
import com.northwind.scheduling.outbox.OutboxEvent;
import com.northwind.scheduling.outbox.OutboxEventType;
import com.northwind.scheduling.outbox.OutboxRepository;
import com.northwind.scheduling.reconciliation.Reconciliation;
import com.northwind.scheduling.reconciliation.ReconciliationService;
import com.northwind.scheduling.service.SchedulingService;

/**
 * MBA-3823 end to end on a real AssessmentDelivery ({@link LegacyEstateDb}): snapshot import -> modern mutations ->
 * outbox -> relay as ad_sched_relay -> legacy rows equal Scheduling (reconcile 0 mismatches, seat-drift 0 rows),
 * and every event replayed twice changes nothing. MBA-3831: capacity notifications go to the stub once, not to legacy.
 */
@EnabledIf("com.northwind.scheduling.relay.LegacyEstateDb#available")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RelayIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        LegacyEstateDb.ensureApplied();
        r.add("spring.datasource.url", () -> LegacyEstateDb.url(LegacyEstateDb.SCHEDULING_DB));
        r.add("spring.datasource.username", LegacyEstateDb::adminUser);
        r.add("spring.datasource.password", LegacyEstateDb::adminPassword);
        r.add("scheduling.legacy.url", () -> LegacyEstateDb.url(LegacyEstateDb.DB));
        r.add("scheduling.legacy.username", () -> "ad_sched_import");
        r.add("scheduling.legacy.password", () -> "Sch3dImport!Dev2026");
        r.add("scheduling.import.on-startup", () -> "true");
        r.add("scheduling.relay.enabled", () -> "true");
        r.add("scheduling.relay.url", () -> LegacyEstateDb.url(LegacyEstateDb.DB));
        r.add("scheduling.relay.username", () -> "ad_sched_relay");
        r.add("scheduling.relay.password", () -> LegacySingleWriterIT.RELAY_PASSWORD);
        r.add("scheduling.relay.poll-ms", () -> "3600000");
        r.add("scheduling.reconciliation.report-dir", () -> "target/reconciliation");
    }

    @Autowired SchedulingService service;
    @Autowired OutboxRepository outbox;
    @Autowired RelayWorker worker;
    @Autowired LegacyRelay relay;
    @Autowired ReconciliationService reconciliation;
    @Autowired JdbcTemplate scheduling;
    @Autowired LoggingNotificationAdapter notifications;

    @Test
    @Order(1)
    void importBaselineIsRelayedAndLegacyMatchesScheduling() {
        assertThat(outbox.pending(10)).extracting(e -> e.type().name()).containsExactly("InventoryBaselined");
        assertThat(worker.relayPending()).isEqualTo(1);
        assertZeroDrift();
    }

    @Test
    @Order(2)
    void bookCancelCapacityUpDownAndAttendanceLandExactlyAsSchedulingDecided() throws SQLException {
        var a = service.book(new SchedulingService.BookCommand(154, "NW00000901", null, null, "PARTNER"));
        var b = service.book(new SchedulingService.BookCommand(154, "NW00000902", null, null, "PORTAL"));
        assertRelayed();
        assertThat(legacyBooking(a.bookingId())).containsEntry("Status", "BOOKED").containsEntry("Channel", "PARTNER");
        assertThat(scheduling.queryForObject("SELECT legacy_booking_id FROM booking WHERE booking_id = ?", Integer.class,
                a.bookingId())).isEqualTo(a.bookingId());

        service.cancel(a.bookingId(), "test cancel");
        assertRelayed();
        assertThat(legacyBooking(a.bookingId())).containsEntry("Status", "CANCELLED");

        int cap = legacyInt("SELECT Capacity FROM dbo.ExamSession WHERE SessionId = 154");
        service.changeCapacity(154, cap + 2, "up");
        assertRelayed();
        int occupied = legacyInt("SELECT COUNT(*) FROM dbo.Booking WHERE SessionId = 154 AND Status IN ('BOOKED','ATTENDED')");
        service.changeCapacity(154, Math.max(2, occupied - 1), "down");
        assertRelayed();
        assertThat(legacyInt("SELECT COUNT(*) FROM dbo.Waitlist w JOIN dbo.Booking b ON b.BookingId = w.BookingId "
                + "WHERE b.SessionId = 154 AND b.Status = 'WAITLISTED'"))
                .isEqualTo(legacyInt("SELECT COUNT(*) FROM dbo.Booking WHERE SessionId = 154 AND Status = 'WAITLISTED'"));

        var c = service.book(new SchedulingService.BookCommand(155, "NW00000903", null, null, "PORTAL"));
        service.recordAttendance(c.bookingId());
        service.recordAttendance(c.bookingId());
        assertRelayed();
        assertThat(legacyBooking(c.bookingId())).containsEntry("Status", "ATTENDED");
        assertThat(b.bookingId()).isPositive();
    }

    @Test
    @Order(3)
    void everyEventReplayedTwiceIsANoOp() throws SQLException {
        Map<String, Integer> before = legacyCounts();
        List<String> ids = relay.checkpointedEventIds();
        assertThat(ids).isNotEmpty();
        List<OutboxEvent> all = scheduling.query("SELECT event_seq FROM outbox_event ORDER BY event_seq",
                (rs, i) -> rs.getLong(1)).isEmpty() ? List.of() : allEvents();
        for (OutboxEvent e : all) {
            assertThat(relay.apply(e)).as("replay of seq " + e.seq()).isFalse();
            assertThat(relay.apply(e)).isFalse();
        }
        assertThat(legacyCounts()).isEqualTo(before);
        assertZeroDrift();
    }

    @Test
    @Order(4)
    void crashAfterLegacyCommitBeforeMarkRelayedReprocessesWithoutDuplicates() throws SQLException {
        service.book(new SchedulingService.BookCommand(154, "NW00000904", null, null, "PORTAL"));
        List<OutboxEvent> pending = outbox.pending(10);
        assertThat(pending).hasSize(1);
        assertThat(relay.apply(pending.get(0))).isTrue();   // legacy committed, "killed" before markRelayed
        Map<String, Integer> before = legacyCounts();
        assertThat(worker.relayPending()).isEqualTo(1);     // restart: reprocessed as checkpointed no-op
        assertThat(legacyCounts()).isEqualTo(before);
        assertZeroDrift();
    }

    @Test
    @Order(5)
    void capacityNotificationsAreDeliveredOnceToTheStubAndNeverWrittenToLegacy() throws SQLException {
        int occupied = legacyInt("SELECT COUNT(*) FROM dbo.Booking WHERE SessionId = 154 AND Status IN ('BOOKED','ATTENDED')");
        Map<String, Integer> before = legacyCounts();
        int sentBefore = notifications.sent(null).size();

        var out = service.changeCapacity(154, Math.max(2, occupied - 1), "MBA-3831 relay");
        List<OutboxEvent> pending = outbox.pending(1000);
        assertThat(pending).extracting(e -> e.type().name()).first().isEqualTo("CapacityChanged");
        assertThat(pending).filteredOn(e -> e.type() == OutboxEventType.CandidateCapacityImpactNotified)
                .hasSize(out.notified().size()).isNotEmpty();
        assertThat(out.notified()).anyMatch(a -> a.kind() == CapacityImpact.Kind.STATUS_CHANGED)
                .anyMatch(a -> a.kind() == CapacityImpact.Kind.SEAT_CONFIRMED);

        assertRelayed();
        assertThat(notifications.sent(null)).hasSize(sentBefore + out.notified().size());
        Map<String, Integer> after = legacyCounts();
        // only the CapacityChanged event reaches legacy: one AuditLog CAPACITY row and one RelayCheckpoint
        assertThat(after.get("AuditLog")).isEqualTo(before.get("AuditLog") + 1);
        assertThat(after.get("RelayCheckpoint")).isEqualTo(before.get("RelayCheckpoint") + 1);
        assertThat(relay.checkpointedEventIds()).doesNotContainAnyElementsOf(pending.stream()
                .filter(e -> e.type() == OutboxEventType.CandidateCapacityImpactNotified)
                .map(e -> e.eventId().toString()).toList());

        // relay retry of every event of this change (crash before markRelayed): nothing recorded or written twice
        scheduling.update("UPDATE outbox_event SET relayed_at = NULL WHERE event_seq >= ?", pending.get(0).seq());
        assertThat(worker.relayPending()).isEqualTo(pending.size());
        assertThat(notifications.sent(null)).hasSize(sentBefore + out.notified().size());
        assertThat(legacyCounts()).isEqualTo(after);
        assertZeroDrift();
    }

    private List<OutboxEvent> allEvents() {
        scheduling.update("UPDATE outbox_event SET relayed_at = NULL");
        try {
            return outbox.pending(100_000);
        } finally {
            scheduling.update("UPDATE outbox_event SET relayed_at = SYSUTCDATETIME()");
        }
    }

    private void assertRelayed() {
        worker.relayPending();
        assertThat(outbox.pending(10)).isEmpty();
        assertZeroDrift();
    }

    private void assertZeroDrift() {
        Reconciliation r = reconciliation.reconcile(false);
        assertThat(r.sessionMismatches()).isEmpty();
        assertThat(r.bookingMismatches()).isEmpty();
        assertThat(r.seatDrift()).isEmpty();
    }

    private static Map<String, Integer> legacyCounts() throws SQLException {
        return Map.of("Booking", legacyInt("SELECT COUNT(*) FROM dbo.Booking"),
                "Waitlist", legacyInt("SELECT COUNT(*) FROM dbo.Waitlist"),
                "AuditLog", legacyInt("SELECT COUNT(*) FROM dbo.AuditLog"),
                "RelayCheckpoint", legacyInt("SELECT COUNT(*) FROM dbo.RelayCheckpoint"));
    }

    private static Map<String, Object> legacyBooking(int id) throws SQLException {
        try (Connection c = LegacyEstateDb.admin(LegacyEstateDb.DB); Statement st = c.createStatement();
             var rs = st.executeQuery("SELECT Status, Channel, SessionId FROM dbo.Booking WHERE BookingId = " + id)) {
            assertThat(rs.next()).as("legacy booking " + id).isTrue();
            return Map.of("Status", rs.getString(1), "Channel", rs.getString(2), "SessionId", rs.getInt(3));
        }
    }

    private static int legacyInt(String sql) throws SQLException {
        try (Connection c = LegacyEstateDb.admin(LegacyEstateDb.DB); Statement st = c.createStatement();
             var rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
