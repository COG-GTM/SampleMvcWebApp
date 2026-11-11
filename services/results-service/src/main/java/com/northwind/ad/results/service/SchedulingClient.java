package com.northwind.ad.results.service;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.northwind.ad.results.web.ApiException;

/**
 * HTTP client for scheduling-service, which owns Booking and ExamSession
 * (docs/to-be/scheduling-service-openapi.yaml). Replaces the direct reads RS-01/RS-03 and the UPDATE RS-02.
 * Transport failures and 5xx answers become 503 SCHEDULING_UNAVAILABLE.
 */
@Component
public class SchedulingClient {

    public static final String UNAVAILABLE = "SCHEDULING_UNAVAILABLE";

    private static final Logger LOG = LoggerFactory.getLogger(SchedulingClient.class);

    private final RestTemplate rest;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private Environment env;

    @Value("${results.audit-actor:results-service}")
    private String actor;

    @Autowired
    public SchedulingClient(RestTemplateBuilder builder,
                            @Value("${scheduling.connect-timeout-ms:2000}") int connectTimeout,
                            @Value("${scheduling.read-timeout-ms:10000}") int readTimeout) {
        this.rest = builder.setConnectTimeout(connectTimeout).setReadTimeout(readTimeout).build();
    }

    // resolved per call so tests can point it at ${local.server.port}
    private String baseUrl() {
        String url = env.resolveRequiredPlaceholders(
                env.getProperty("scheduling.base-url", "http://localhost:8084/api/v1"));
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** GET /bookings/{id}; null on 404. */
    public BookingView getBooking(int bookingId) {
        try {
            return rest.getForObject(baseUrl() + "/bookings/{id}", BookingView.class, bookingId);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw translate(e, "GET /bookings/" + bookingId);
        } catch (ResourceAccessException e) {
            throw unavailable(e, "GET /bookings/" + bookingId);
        }
    }

    /** Newest seat-holding booking of a candidate on a session (legacy findBookingId: BookingId DESC), or null. */
    public Integer findLatestActiveBookingId(String candidateNumber, int sessionId) {
        URI url = UriComponentsBuilder.fromHttpUrl(baseUrl() + "/bookings")
                .queryParam("candidateNumber", queryValue(candidateNumber))
                .queryParam("sessionId", sessionId)
                .queryParam("status", "BOOKED%2CATTENDED")
                .queryParam("sort", "bookingId%2Cdesc")
                .queryParam("limit", 1)
                .build(true).toUri();
        try {
            BookingView[] found = rest.getForObject(url, BookingView[].class);
            List<BookingView> list = found == null ? Collections.<BookingView>emptyList() : Arrays.asList(found);
            Integer best = null;
            for (BookingView b : list) { // defensive: keep the legacy ordering even if the server ignores sort/limit
                if (sessionId == b.getSessionId() && ("BOOKED".equals(b.getStatus()) || "ATTENDED".equals(b.getStatus()))
                        && (best == null || b.getBookingId() > best)) {
                    best = b.getBookingId();
                }
            }
            return best;
        } catch (HttpStatusCodeException e) {
            throw translate(e, "GET /bookings");
        } catch (ResourceAccessException e) {
            throw unavailable(e, "GET /bookings");
        }
    }

    /**
     * POST /bookings/{id}/attendance with the result block (narrative section 3). BOOKED -> ATTENDED, ATTENDED is a
     * no-op; 404 -> BOOKING_NOT_FOUND, 409 -> BOOKING_NOT_ACTIVE (status changed after our read).
     */
    public void markAttended(int bookingId, int resultId, int score, boolean passed, Date scoredAt) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("resultId", resultId);
        result.put("score", score);
        result.put("passed", passed);
        result.put("scoredAt", ResultIngestService.format(scoredAt));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Actor", actor);
        HttpEntity<Map<String, Object>> req = new HttpEntity<Map<String, Object>>(
                Collections.<String, Object>singletonMap("result", result), headers);
        try {
            rest.postForEntity(baseUrl() + "/bookings/{id}/attendance", req, String.class, bookingId);
        } catch (HttpStatusCodeException e) {
            throw translate(e, "POST /bookings/" + bookingId + "/attendance");
        } catch (ResourceAccessException e) {
            throw unavailable(e, "POST /bookings/" + bookingId + "/attendance");
        }
    }

    private ApiException translate(HttpStatusCodeException e, String call) {
        if (e.getStatusCode().is5xxServerError()) {
            return unavailable(e, call);
        }
        String error = null;
        String message = null;
        try {
            Map<?, ?> body = json.readValue(e.getResponseBodyAsString(), Map.class);
            error = (String) body.get("error");
            message = (String) body.get("message");
        } catch (Exception ignored) {
            // non-JSON body: fall back to the status line
        }
        if (error == null) {
            error = e.getStatusCode() == HttpStatus.NOT_FOUND ? "BOOKING_NOT_FOUND" : "SCHEDULING_" + e.getStatusCode().value();
        }
        return new ApiException(e.getStatusCode(), error, message != null ? message : call + " -> " + e.getStatusCode());
    }

    private ApiException unavailable(Exception e, String call) {
        LOG.warn("scheduling-service unavailable on {}: {}", call, e.getMessage());
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE,
                "scheduling-service unavailable (" + call + "), nothing was recorded for this result; retry later");
    }

    /** Form-encodes a query value: UriComponents.encode() leaves '+', which the server decodes as a space. */
    private static String queryValue(String v) {
        try {
            return URLEncoder.encode(v, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
