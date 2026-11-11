package com.northwind.scheduling.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.northwind.scheduling.SqlServerTestBase;
import com.northwind.scheduling.importer.SnapshotImporter;
import com.northwind.scheduling.outbox.OutboxEvent;
import com.northwind.scheduling.outbox.OutboxEventType;
import com.northwind.scheduling.outbox.OutboxRepository;

/**
 * MBA-3831 acceptance criteria on Testcontainers SQL Server: PUT capacity -> one CandidateCapacityImpactNotified outbox
 * row per affected candidate (same transaction as CapacityChanged) -> NotificationDelivery (what the relay calls) ->
 * stub, readable at GET /internal/notifications. The relay itself is covered by RelayIT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SqlServerTestBase.FixtureLegacy.class)
class CapacityImpactNotificationIT extends SqlServerTestBase {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };

    @Autowired TestRestTemplate http;
    @Autowired SnapshotImporter importer;
    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxRepository outbox;
    @Autowired NotificationDelivery delivery;
    @Autowired ObjectMapper json;

    long seqBefore;

    @BeforeEach
    void reset() {
        importer.run(SnapshotImporter.Mode.RESET);
        jdbc.update("UPDATE outbox_event SET relayed_at = SYSUTCDATETIME() WHERE relayed_at IS NULL");
        Long max = jdbc.queryForObject("SELECT MAX(event_seq) FROM outbox_event", Long.class);
        seqBefore = max == null ? 0 : max;
    }

    @Test
    @SuppressWarnings("unchecked")
    void reductionOnTenBookedGivesTwoStatusChangedAndEightSeatConfirmedNotifications() throws Exception {
        // fixture session 154: capacity 20, bookings 1-9 BOOKED for NW00000901..909; book a 10th
        ResponseEntity<Map<String, Object>> tenth = http.exchange("/api/v2/bookings", HttpMethod.POST,
                new HttpEntity<>(Map.of("sessionId", 154, "candidateNumber", "NW00000920")), MAP);
        assertThat(tenth.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        int tenthId = (Integer) tenth.getBody().get("bookingId");
        seqBefore = jdbc.queryForObject("SELECT MAX(event_seq) FROM outbox_event", Long.class);

        Map<String, Object> r = capacity(154, 8);
        assertThat(r).containsEntry("oldCapacity", 20).containsEntry("newCapacity", 8).containsEntry("notifiedCandidates", 10);
        assertThat((List<Object>) r.get("demotedBookingIds")).containsExactlyInAnyOrder(9, tenthId);

        List<OutboxEvent> events = newEvents();
        assertThat(events.get(0).type()).isEqualTo(OutboxEventType.CapacityChanged);
        List<CandidateCapacityImpactNotified> sent = events.stream()
                .filter(e -> e.type() == OutboxEventType.CandidateCapacityImpactNotified)
                .map(e -> e.payload().notification()).toList();
        assertThat(sent).hasSize(10).allSatisfy(n -> {
            assertThat(n.sessionId()).isEqualTo(154);
            assertThat(n.centerCode()).isEqualTo("SFO01");
            assertThat(n.examCode()).isEqualTo("MHL-RN1");
            assertThat(n.startsAt()).isEqualTo(java.time.LocalDateTime.of(2026, 11, 12, 13, 30));
            assertThat(n.oldCapacity()).isEqualTo(20);
            assertThat(n.newCapacity()).isEqualTo(8);
        });
        assertThat(sent).filteredOn(n -> n.impact() == com.northwind.scheduling.domain.CapacityImpact.Kind.STATUS_CHANGED)
                .extracting(CandidateCapacityImpactNotified::candidateNumber, CandidateCapacityImpactNotified::bookingId,
                        CandidateCapacityImpactNotified::previousStatus, CandidateCapacityImpactNotified::newStatus)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("NW00000909", 9, "BOOKED", "WAITLISTED"),
                        org.assertj.core.groups.Tuple.tuple("NW00000920", tenthId, "BOOKED", "WAITLISTED"));
        assertThat(sent).filteredOn(n -> n.impact() == com.northwind.scheduling.domain.CapacityImpact.Kind.SEAT_CONFIRMED)
                .hasSize(8).allSatisfy(n -> assertThat(n.previousStatus() + "->" + n.newStatus()).isEqualTo("BOOKED->BOOKED"))
                .extracting(CandidateCapacityImpactNotified::candidateNumber)
                .containsExactlyInAnyOrder("NW00000901", "NW00000902", "NW00000903", "NW00000904", "NW00000905",
                        "NW00000906", "NW00000907", "NW00000908");

        // stored payloads conform to docs/schemas and carry the outbox event id
        var schema = CandidateCapacityImpactSchemaTest.schema();
        for (Map<String, Object> row : jdbc.queryForList("SELECT event_id, payload FROM outbox_event WHERE event_seq > ? "
                + "AND event_type = 'CandidateCapacityImpactNotified'", seqBefore)) {
            JsonNode n = json.readTree((String) row.get("payload")).get("notification");
            assertThat(schema.validate(n)).isEmpty();
            assertThat(n.get("eventId").asText()).isEqualToIgnoringCase(row.get("event_id").toString());
        }

        deliverNewEventsTwice();
        List<Map<String, Object>> demoted = notifications("NW00000909");
        assertThat(demoted).hasSize(1);
        assertThat(demoted.get(0)).containsEntry("previousStatus", "BOOKED").containsEntry("newStatus", "WAITLISTED")
                .containsEntry("impact", "STATUS_CHANGED").containsEntry("bookingId", 9).containsEntry("centerCode", "SFO01")
                .containsEntry("startsAt", "2026-11-12T13:30:00");
        assertThat(notifications("NW00000901")).singleElement().satisfies(n -> assertThat(n)
                .containsEntry("impact", "SEAT_CONFIRMED").containsEntry("newStatus", "BOOKED"));
    }

    @Test
    void increaseWithoutStatusChangesEmitsNoNotification() {
        Map<String, Object> r = capacity(154, 25);
        assertThat(r).containsEntry("notifiedCandidates", 0);
        assertThat(newEvents()).extracting(OutboxEvent::type).containsExactly(OutboxEventType.CapacityChanged);
    }

    @Test
    void sameEventDeliveredTwiceIsRecordedOnce() {
        capacity(154, 7);   // 9 booked: bookings 8 and 9 demoted, 1-7 confirmed
        List<OutboxEvent> notifications = newEvents().stream()
                .filter(e -> e.type() == OutboxEventType.CandidateCapacityImpactNotified).toList();
        assertThat(notifications).hasSize(9);

        OutboxEvent eight = notifications.stream().filter(e -> e.payload().notification().bookingId() == 8).findFirst()
                .orElseThrow();
        int before = notifications("NW00000908").size();
        assertThat(delivery.deliver(eight)).isTrue();
        assertThat(delivery.deliver(eight)).isFalse();   // relay retry of the same event_id
        List<Map<String, Object>> recorded = notifications("NW00000908");
        assertThat(recorded).hasSize(before + 1);
        assertThat(recorded.get(recorded.size() - 1)).containsEntry("eventId", eight.eventId().toString())
                .containsEntry("newStatus", "WAITLISTED");
    }

    @Test
    void notificationsEndpointIsInternalOnlyAndFiltersByCandidate() {
        ResponseEntity<List<Map<String, Object>>> unknown = http.exchange("/internal/notifications?candidateNumber=NW09999999",
                HttpMethod.GET, null, LIST);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unknown.getBody()).isEmpty();
        assertThat(http.getForEntity("/internal/notifications", String.class).getStatusCode().value()).isEqualTo(400);
        assertThat(http.getForEntity("/internal/notifications?candidateNumber=+", String.class).getStatusCode().value())
                .isEqualTo(400);
        assertThat(http.getForObject("/v3/api-docs", String.class)).doesNotContain("/internal/notifications");
    }

    private Map<String, Object> capacity(int sessionId, int capacity) {
        ResponseEntity<Map<String, Object>> r = http.exchange("/api/v2/sessions/" + sessionId + "/capacity", HttpMethod.PUT,
                new HttpEntity<>(Map.of("capacity", capacity, "reason", "MBA-3831 IT")), MAP);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private List<OutboxEvent> newEvents() {
        return outbox.pending(1000).stream().filter(e -> e.seq() > seqBefore).toList();
    }

    private void deliverNewEventsTwice() {
        for (int i = 0; i < 2; i++) {
            newEvents().stream().filter(e -> e.type() == OutboxEventType.CandidateCapacityImpactNotified)
                    .forEach(delivery::deliver);
        }
    }

    private List<Map<String, Object>> notifications(String candidateNumber) {
        return http.exchange("/internal/notifications?candidateNumber=" + candidateNumber, HttpMethod.GET, null, LIST)
                .getBody();
    }
}
