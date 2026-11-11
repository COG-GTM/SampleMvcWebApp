package com.northwind.assessment.gateway.translate;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class V1ShapesTest {

    private static String v1(String v2Json, java.util.List<String> fields) throws Exception {
        return lf(V1Shapes.write(V1Shapes.project(V1Shapes.parse(v2Json.getBytes()), fields)));
    }

    /** Jackson's pretty printer ends lines with the platform separator (CRLF on Windows); compare with LF. */
    private static String lf(byte[] json) {
        return new String(json, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    void postBookingResponseKeepsOnlyBookingIdAndStatusInBookingApiFormat() throws Exception {
        String out = v1("{\"bookingId\":50000001,\"status\":\"BOOKED\",\"sessionId\":1,\"candidateNumber\":\"NW00000901\","
                + "\"accommodationCode\":null,\"channel\":\"PARTNER\",\"waitlistPosition\":null,\"seatsAvailable\":11}",
                V1Shapes.BOOKING_RESPONSE_FIELDS);
        assertThat(out).isEqualTo("{\n  \"bookingId\" : 50000001,\n  \"status\" : \"BOOKED\"\n}");
    }

    @Test
    void bookingDropsV2FieldsKeepsOrderNullsAndNormalizesTimestamps() throws Exception {
        String out = v1("{\"origin\":\"MODERN\",\"legacyBookingId\":null,\"waitlistPosition\":null,"
                + "\"cancelReason\":\"Cancelled by partner\",\"cancelledAt\":\"2026-11-10T10:00:00.123Z\","
                + "\"bookingId\":50000001,\"sessionId\":1,\"candidateNumber\":\"NW00000901\",\"examCode\":\"CTA-NET1\","
                + "\"sessionStartsAt\":\"2026-12-01T09:00:00\",\"status\":\"CANCELLED\",\"channel\":\"PARTNER\","
                + "\"bookedAt\":\"2026-11-10T09:00:00\"}", V1Shapes.BOOKING_FIELDS);
        assertThat(out).isEqualTo("""
                {
                  "bookingId" : 50000001,
                  "sessionId" : 1,
                  "candidateNumber" : "NW00000901",
                  "examCode" : "CTA-NET1",
                  "sessionStartsAt" : "2026-12-01T09:00:00",
                  "status" : "CANCELLED",
                  "accommodationCode" : null,
                  "channel" : "PARTNER",
                  "externalRef" : null,
                  "bookedAt" : "2026-11-10T09:00:00",
                  "cancelledAt" : "2026-11-10T10:00:00",
                  "cancelReason" : "Cancelled by partner"
                }""");
    }

    @Test
    void errorDropsLegacyCode() throws Exception {
        assertThat(v1("{\"error\":\"SESSION_NOT_OPEN\",\"message\":\"Session 1 is not open\",\"legacyCode\":50001}",
                V1Shapes.ERROR_FIELDS))
                .isEqualTo("{\n  \"error\" : \"SESSION_NOT_OPEN\",\n  \"message\" : \"Session 1 is not open\"\n}");
    }

    @Test
    void sessionListUsesBookingApiArrayFormatAndNullBookedSeats() throws Exception {
        String out = lf(V1Shapes.write(V1Shapes.sessionList(V1Shapes.parse(
                "[{\"sessionId\":7,\"bookedSeats\":3,\"accommodationUsed\":0,\"waitlistCount\":2}]".getBytes()))));
        assertThat(out).startsWith("[ {\n  \"sessionId\" : 7,").contains("\"bookedSeats\" : null\n} ]")
                .doesNotContain("waitlistCount").doesNotContain("accommodationUsed");
    }

    @Test
    void localDateTimeKeepsUnknownFormats() {
        assertThat(V1Shapes.localDateTime("2026-09-01T09:00:00+01:00")).isEqualTo("2026-09-01T09:00:00");
        assertThat(V1Shapes.localDateTime("soon")).isEqualTo("soon");
    }
}
