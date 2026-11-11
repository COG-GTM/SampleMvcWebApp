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
                List.of(new OutboxPayload.SessionState(42, 20, 2, 7, 13, 1, "OPEN", 0)),
                List.of(new OutboxPayload.BookingState(50_000_123L, "MODERN", null, 42, 9, "BOOKED", "ET25", "PARTNER",
                        "P-1", T, null, null, null, null)),
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
        assertThat(OutboxEventType.values()).hasSize(10);
    }

    /** Offer accepted: the offered booking is BOOKED and off the waitlist; the next entry moved from 3 to 2. */
    private static OutboxPayload acceptedPayload() {
        return new OutboxPayload("SeatOfferAccepted", T, "candidate",
                List.of(new OutboxPayload.SessionState(42, 20, 0, 0, 20, 0, "OPEN", 0)),
                List.of(new OutboxPayload.BookingState(1001L, "LEGACY", 1001, 42, 9, "BOOKED", null, "PORTAL", null, T, null,
                                null, null, null),
                        new OutboxPayload.BookingState(1002L, "LEGACY", 1002, 42, 10, "WAITLISTED", null, "PORTAL", null, T,
                                null, null, 1, null)),
                Map.of("offerId", 7, "shiftedBookingIds", List.of(1002)));
    }

    @Test
    void seatOfferEvents_haveAuditActionsThatFitLegacyColumns() {
        assertThat(OutboxEventType.SeatOffered.auditAction()).isEqualTo("OFFER");
        assertThat(OutboxEventType.SeatOfferAccepted.auditAction()).isEqualTo("OFFER_ACCEPT");
        assertThat(OutboxEventType.SeatOfferDeclined.auditAction()).isEqualTo("OFFER_DECLINE");
        assertThat(OutboxEventType.SeatOfferExpired.auditAction()).isEqualTo("OFFER_EXPIRE");
        for (OutboxEventType t : OutboxEventType.values()) {
            assertThat(t.auditAction().length()).as("dbo.AuditLog.Action VARCHAR(30)").isLessThanOrEqualTo(30);
            assertThat(t.name().length()).as("dbo.RelayCheckpoint.EventType VARCHAR(40)").isLessThanOrEqualTo(40);
        }
    }

    @Test
    void seatOffered_auditDetailShowsHeldSeats_andPayloadCarriesOfferExpiry() throws Exception {
        LocalDateTime expires = T.plusHours(24);
        OutboxPayload offered = new OutboxPayload("SeatOffered", T, "scheduling-service",
                List.of(new OutboxPayload.SessionState(42, 20, 0, 0, 19, 0, "OPEN", 1)),
                List.of(new OutboxPayload.BookingState(1001L, "LEGACY", 1001, 42, 9, "WAITLISTED", null, "PORTAL", null, T,
                        null, null, 1, expires)),
                Map.of("offerId", 7));
        assertThat(LegacyProjector.auditDetail(18, offered, Map.of(1001L, 1001)))
                .isEqualTo("outbox event=18 SeatOffered session=42 capacity=20 seats=0 offered=1 booking=1001/1001 WAITLISTED "
                        + "by=scheduling-service");
        ObjectMapper m = new ObjectMapper().registerModule(new JavaTimeModule());
        OutboxPayload back = m.readValue(m.writeValueAsString(offered), OutboxPayload.class);
        assertThat(back.bookings().get(0).offerExpiresAt()).isEqualTo(expires);
        assertThat(back.sessions().get(0).seatsOffered()).isEqualTo(1);
    }

    @Test
    void seatOfferAccepted_carriesBookedBookingWithoutPosition_andShiftedPositions() throws Exception {
        ObjectMapper m = new ObjectMapper().registerModule(new JavaTimeModule());
        OutboxPayload back = m.readValue(m.writeValueAsString(acceptedPayload()), OutboxPayload.class);
        assertThat(back).isEqualTo(acceptedPayload());
        assertThat(back.bookings().get(0).waitlistPosition()).isNull();
        assertThat(back.bookings().get(1).waitlistPosition()).isEqualTo(1);
        assertThat(LegacyProjector.auditDetail(19, back, Map.of(1001L, 1001, 1002L, 1002)))
                .isEqualTo("outbox event=19 SeatOfferAccepted session=42 capacity=20 seats=0 booking=1001/1001 BOOKED "
                        + "booking=1002/1002 WAITLISTED by=candidate");
    }

    @Test
    void pre3829Payload_withoutOfferFields_stillDeserialises() throws Exception {
        String json = "{\"eventType\":\"BookingCreated\",\"occurredAt\":\"2026-11-10T09:30:00\",\"actor\":\"x\","
                + "\"sessions\":[{\"sessionId\":42,\"capacity\":20,\"accommodationCapacity\":2,\"seatsAvailable\":7,"
                + "\"seatsHeld\":13,\"accommodationSeatsUsed\":1,\"status\":\"OPEN\"}],\"bookings\":[],\"detail\":{}}";
        OutboxPayload p = new ObjectMapper().registerModule(new JavaTimeModule()).readValue(json, OutboxPayload.class);
        assertThat(p.sessions().get(0).seatsOffered()).isZero();
    }
}
