package com.northwind.assessment.booking.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HTTP client for scheduling-service (docs/to-be/scheduling-service-openapi.yaml), the single writer of
 * ExamSession, Booking and Waitlist. Commands send X-Actor and a fresh Idempotency-Key. Non-2xx answers become
 * SchedulingException carrying the service's {error, message, legacyState}.
 */
@Component
public class SchedulingClient {

    private final RestTemplate rest;
    private final String baseUrl;
    /** The only 5xx error code in the contract: a SQL failure inside the service (e.g. deadlock 1205). */
    public static final String SYSTEM_ERROR = "SYSTEM_ERROR";

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public SchedulingClient(RestTemplateBuilder builder,
                            @Value("${scheduling.base-url:http://localhost:8084/api/v1}") String baseUrl,
                            @Value("${scheduling.timeout-ms:30000}") int timeoutMs) {
        this(builder.setConnectTimeout(timeoutMs).setReadTimeout(timeoutMs).build(), baseUrl);
    }

    public SchedulingClient(RestTemplate rest, String baseUrl) {
        this.rest = rest;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public RestTemplate getRestTemplate() {
        return rest;
    }

    // ------------------------------------------------------------- sessions

    /** null when the service answers 404 SESSION_NOT_FOUND */
    public JsonNode getSession(int sessionId) {
        return getOrNull("/sessions/" + sessionId);
    }

    public JsonNode searchSessions(Map<String, Object> query) {
        return exchange(HttpMethod.GET, url("/sessions", query), null, null);
    }

    // ------------------------------------------------------------- bookings

    /** null when the service answers 404 BOOKING_NOT_FOUND */
    public JsonNode getBooking(int bookingId) {
        return getOrNull("/bookings/" + bookingId);
    }

    public JsonNode searchBookings(Map<String, Object> query) {
        return exchange(HttpMethod.GET, url("/bookings", query), null, null);
    }

    public JsonNode book(int sessionId, int candidateId, String accommodationCode, String channel,
                         String externalRef, String actor) {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("sessionId", sessionId);
        body.put("candidateId", candidateId);
        body.put("accommodationCode", accommodationCode);
        body.put("channel", channel);
        body.put("externalRef", externalRef);
        return exchange(HttpMethod.POST, baseUrl + "/bookings", body, actor);
    }

    public JsonNode cancel(int bookingId, String reason, String actor) {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("reason", reason);
        return exchange(HttpMethod.POST, baseUrl + "/bookings/" + bookingId + "/cancel", body, actor);
    }

    public JsonNode reschedule(int bookingId, int newSessionId, String channel, String actor) {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("newSessionId", newSessionId);
        body.put("channel", channel);
        return exchange(HttpMethod.POST, baseUrl + "/bookings/" + bookingId + "/reschedule", body, actor);
    }

    // ------------------------------------------------------------- transport

    private JsonNode getOrNull(String path) {
        try {
            return exchange(HttpMethod.GET, baseUrl + path, null, null);
        } catch (SchedulingException e) {
            if (e.getHttpStatus() == 404) {
                return null;
            }
            throw e;
        }
    }

    private String url(String path, Map<String, Object> query) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + path);
        if (query != null) {
            for (Map.Entry<String, Object> e : query.entrySet()) {
                if (e.getValue() != null && e.getValue().toString().length() > 0) {
                    b.queryParam(e.getKey(), e.getValue());
                }
            }
        }
        return b.build().encode().toUriString();
    }

    private JsonNode exchange(HttpMethod method, String url, Object body, String actor) {
        HttpHeaders headers = new HttpHeaders();
        List<MediaType> accept = new ArrayList<MediaType>();
        accept.add(MediaType.APPLICATION_JSON);
        headers.setAccept(accept);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (actor != null) {
            headers.set("X-Actor", actor);
            headers.set("Idempotency-Key", UUID.randomUUID().toString());
        }
        try {
            return rest.exchange(url, method, new HttpEntity<Object>(body, headers), JsonNode.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw toException(e);
        } catch (ResourceAccessException e) {
            throw new SchedulingException(503, "SCHEDULING_UNAVAILABLE",
                    "scheduling-service unreachable: " + e.getMostSpecificCause().getMessage(), null);
        }
    }

    private SchedulingException toException(HttpStatusCodeException e) {
        int status = e.getRawStatusCode();
        try {
            JsonNode err = json.readTree(e.getResponseBodyAsString());
            boolean contractError = err != null && err.hasNonNull("message")
                    && (status < 500 || SYSTEM_ERROR.equals(err.path("error").asText(null)));
            if (contractError) {
                return new SchedulingException(status, err.path("error").asText(null), err.get("message").asText(),
                        err.hasNonNull("legacyState") ? Integer.valueOf(err.get("legacyState").asInt()) : null);
            }
        } catch (Exception ignored) {
            // not a contract error body
        }
        return new SchedulingException(status, status >= 500 ? "SCHEDULING_UNAVAILABLE" : "SCHEDULING_ERROR",
                "scheduling-service returned HTTP " + status, null);
    }
}
