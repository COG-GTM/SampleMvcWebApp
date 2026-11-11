package com.northwind.assessment.gateway.translate;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Maps scheduling-service {@code /api/v2} JSON back to booking-api v1 JSON, byte-compatible with booking-api
 * (Jackson pretty printer, field order of the v1 model classes, explicit nulls, {@code yyyy-MM-dd'T'HH:mm:ss}
 * timestamps, no trailing newline). Field lists are taken from {@code services/booking-api} model classes.
 */
public final class V1Shapes {

    /** booking-api {@code SessionInfo}. */
    public static final List<String> SESSION_FIELDS = List.of("sessionId", "examCode", "examTitle", "centerCode",
            "centerName", "roomName", "startsAt", "capacity", "accommodationCapacity", "seatsAvailable", "status",
            "bookedSeats");
    /** booking-api {@code BookingInfo}. */
    public static final List<String> BOOKING_FIELDS = List.of("bookingId", "sessionId", "candidateNumber", "examCode",
            "sessionStartsAt", "status", "accommodationCode", "channel", "externalRef", "bookedAt", "cancelledAt",
            "cancelReason");
    /** booking-api {@code BookingResponse} (POST bookings, reschedule). */
    public static final List<String> BOOKING_RESPONSE_FIELDS = List.of("bookingId", "status");
    /** booking-api {@code EligibilityResult}. */
    public static final List<String> ELIGIBILITY_FIELDS = List.of("eligible", "reason", "earliestRetakeDate");
    /** booking-api {@code ApiExceptionHandler} body; v2 {@code legacyCode} is dropped. */
    public static final List<String> ERROR_FIELDS = List.of("error", "message");

    private static final List<String> TIMESTAMP_FIELDS = List.of("startsAt", "sessionStartsAt", "bookedAt", "cancelledAt");
    private static final Pattern LOCAL_DATE_TIME = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})");

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private V1Shapes() {
    }

    public static JsonNode parse(byte[] body) throws java.io.IOException {
        return MAPPER.readTree(body);
    }

    public static ObjectNode project(JsonNode v2, List<String> fields) {
        ObjectNode out = NODES.objectNode();
        for (String f : fields) {
            JsonNode v = v2 == null ? null : v2.get(f);
            if (v == null || v.isMissingNode()) {
                out.putNull(f);
            } else if (TIMESTAMP_FIELDS.contains(f) && v.isTextual()) {
                out.put(f, localDateTime(v.asText()));
            } else {
                out.set(f, v);
            }
        }
        return out;
    }

    /** {@code GET /api/v1/sessions} leaves {@code bookedSeats} null for list results (SessionDao list query). */
    public static ArrayNode sessionList(JsonNode v2) {
        ArrayNode out = NODES.arrayNode();
        if (v2 != null && v2.isArray()) {
            for (JsonNode s : v2) {
                ObjectNode o = project(s, SESSION_FIELDS);
                o.putNull("bookedSeats");
                out.add(o);
            }
        }
        return out;
    }

    public static ObjectNode error(String code, String message) {
        ObjectNode out = NODES.objectNode();
        out.put("error", code);
        out.put("message", message);
        return out;
    }

    public static byte[] write(JsonNode node) {
        try {
            return MAPPER.writeValueAsBytes(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String localDateTime(String text) {
        Matcher m = LOCAL_DATE_TIME.matcher(text);
        return m.find() ? m.group(1) : text;
    }
}
