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
