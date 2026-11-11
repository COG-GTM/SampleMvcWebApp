package com.northwind.assessment.booking.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.northwind.assessment.booking.web.ApiException;

/**
 * Booking writes go to scheduling-service through the routing layer (MBA-3823): since
 * db/security/040_single_writer.sql this service's login can no longer write dbo.Booking, dbo.Waitlist or the
 * ExamSession seat columns. scheduling-service returns the v1 error codes, which are passed through unchanged.
 */
@Component
public class SchedulingClient {

    private static final Logger log = LoggerFactory.getLogger(SchedulingClient.class);

    private final RestTemplate rest;
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public SchedulingClient(RestTemplateBuilder builder,
                            @Value("${booking.scheduling-url:http://localhost:8000}") String baseUrl,
                            @Value("${booking.scheduling-timeout-ms:10000}") int timeoutMs) {
        this(builder.setConnectTimeout(timeoutMs).setReadTimeout(timeoutMs).build(), baseUrl);
    }

    SchedulingClient(RestTemplate rest, String baseUrl) {
        this.rest = rest;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public Map<String, Object> book(int sessionId, String candidateNumber, String accommodationCode, String externalRef,
                                    String channel) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("sessionId", sessionId);
        body.put("candidateNumber", candidateNumber);
        body.put("accommodationCode", accommodationCode);
        body.put("externalRef", externalRef);
        body.put("channel", channel);
        return exchange(HttpMethod.POST, "/api/v2/bookings", body);
    }

    public Map<String, Object> cancel(int bookingId, String reason) {
        return exchange(HttpMethod.DELETE, "/api/v2/bookings/" + bookingId + "?reason={reason}", null, reason);
    }

    public Map<String, Object> reschedule(int bookingId, int newSessionId) {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("newSessionId", newSessionId);
        return exchange(HttpMethod.POST, "/api/v2/bookings/" + bookingId + "/reschedule", body);
    }

    public Map<String, Object> find(long bookingId) {
        return exchange(HttpMethod.GET, "/api/v2/bookings/" + bookingId, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchange(HttpMethod method, String path, Object body, Object... vars) {
        try {
            org.springframework.http.HttpEntity<Object> entity = body == null ? null
                    : new org.springframework.http.HttpEntity<Object>(body);
            return rest.exchange(baseUrl + path, method, entity, Map.class, vars).getBody();
        } catch (HttpStatusCodeException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            log.error("scheduling-service unreachable at {}: {}", baseUrl, e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SCHEDULING_UNAVAILABLE",
                    "Booking writes are handled by scheduling-service, which is not reachable");
        }
    }

    ApiException translate(HttpStatusCodeException e) {
        String error = "SCHEDULING_ERROR";
        String message = e.getStatusText();
        try {
            Map<?, ?> m = mapper.readValue(e.getResponseBodyAsString(), Map.class);
            if (m.get("error") != null) {
                error = String.valueOf(m.get("error"));
            }
            if (m.get("message") != null) {
                message = String.valueOf(m.get("message"));
            }
        } catch (IOException | RuntimeException ignored) {
            // non-JSON body: keep the status text
        }
        HttpStatus status = e.getStatusCode().is5xxServerError() ? HttpStatus.BAD_GATEWAY : e.getStatusCode();
        return new ApiException(status, error, message);
    }
}
