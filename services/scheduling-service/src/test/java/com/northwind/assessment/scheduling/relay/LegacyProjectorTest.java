package com.northwind.assessment.scheduling.relay;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.northwind.assessment.scheduling.outbox.OutboxEventType;
import com.northwind.assessment.scheduling.outbox.OutboxPayload;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegacyProjectorTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 10, 9, 30);

    private static OutboxPayload payload() {
        return new OutboxPayload("BookingCreated", T, "partner",
                List.of(new OutboxPayload.SessionState(42, 20, 2, 7, 13, 1, "OPEN")),
                List.of(new OutboxPayload.BookingState(50_000_123L, "MODERN", null, 42, 9, "BOOKED", "ET25", "PARTNER",
                        "P-1", T, null, null, null)),
                Map.of("channel", "PARTNER"));
    }

    @Test
    void auditDetail_namesEventSessionSeatsAndBothBookingIds() {
        String d = LegacyProjector.auditDetail(17, payload(), Map.of(50_000_123L, 4321));
        assertThat(d).isEqualTo("outbox event=17 BookingCreated session=42 capacity=20 seats=7 booking=4321/50000123 BOOKED by=partner");
    }

    private static OutboxPayload capacityChanged(Map<String, Object> detail, List<OutboxPayload.BookingState> bumped) {
        return new OutboxPayload("CapacityChanged", T, "admin-ui",
                List.of(new OutboxPayload.SessionState(42, 18, 2, 5, 13, 1, "OPEN")), bumped, detail);
    }

    @Test
    void auditDetail_capacityChanged_carriesOldAndNewCapacityBeforeActor() {
        String d = LegacyProjector.auditDetail(18, capacityChanged(
                Map.of("oldCapacity", 20, "newCapacity", 18, "bumpedBookingIds", List.of()), List.of()), Map.of());
        assertThat(d).isEqualTo("outbox event=18 CapacityChanged session=42 capacity=18 seats=5 change=20 -> 18 by=admin-ui");
    }

    @Test
    void auditDetail_capacityChanged_withBumpedBooking_keepsBookingTokensThenChange() {
        OutboxPayload.BookingState bumped = new OutboxPayload.BookingState(77L, "LEGACY_IMPORT", 77, 42, 9, "WAITLISTED", null,
                "PORTAL", null, T, null, null, 1);
        String d = LegacyProjector.auditDetail(19, capacityChanged(
                Map.of("oldCapacity", 14, "newCapacity", 12, "bumpedBookingIds", List.of(77L)), List.of(bumped)), Map.of(77L, 77));
        assertThat(d).isEqualTo("outbox event=19 CapacityChanged session=42 capacity=18 seats=5 booking=77/77 WAITLISTED "
                + "change=14 -> 12 by=admin-ui");
    }

    @Test
    void auditDetail_capacityChanged_afterJsonRoundTrip_stillCarriesChange() throws Exception {
        ObjectMapper m = new ObjectMapper().registerModule(new JavaTimeModule());
        OutboxPayload p = m.readValue(m.writeValueAsString(capacityChanged(
                Map.of("oldCapacity", 20, "newCapacity", 25, "bumpedBookingIds", List.of()), List.of())), OutboxPayload.class);
        assertThat(LegacyProjector.auditDetail(20, p, Map.of())).contains(" change=20 -> 25 by=admin-ui");
    }

    @Test
    void auditDetail_capacityChangedWithoutOldCapacity_hasNoChangeToken() {
        String d = LegacyProjector.auditDetail(21, capacityChanged(Map.of("newCapacity", 18), List.of()), Map.of());
        assertThat(d).isEqualTo("outbox event=21 CapacityChanged session=42 capacity=18 seats=5 by=admin-ui");
    }

    @Test
    void auditDetail_otherEvents_neverCarryChange() {
        OutboxPayload p = new OutboxPayload("BookingCreated", T, "partner", payload().sessions(), payload().bookings(),
                Map.of("oldCapacity", 1, "newCapacity", 2));
        assertThat(LegacyProjector.auditDetail(17, p, Map.of(50_000_123L, 4321))).doesNotContain("change=");
    }

    @Test
    void payload_roundTripsThroughJson() throws Exception {
        ObjectMapper m = new ObjectMapper().registerModule(new JavaTimeModule());
        OutboxPayload p = payload();
        assertThat(m.readValue(m.writeValueAsString(p), OutboxPayload.class)).isEqualTo(p);
    }

    @Test
    void auditActions_matchLegacyProcVocabulary() {
        assertThat(OutboxEventType.BookingCreated.auditAction()).isEqualTo("BOOK");
        assertThat(OutboxEventType.BookingCancelled.auditAction()).isEqualTo("CANCEL");
        assertThat(OutboxEventType.CapacityChanged.auditAction()).isEqualTo("CAPACITY");
        assertThat(OutboxEventType.values()).hasSize(6);
    }
}
