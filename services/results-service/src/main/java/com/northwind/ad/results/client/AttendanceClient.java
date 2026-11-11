package com.northwind.ad.results.client;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.northwind.ad.results.web.ApiException;

/**
 * Records attendance in scheduling-service ({@code POST /api/v2/bookings/{bookingId}/attendance}), the single
 * writer of bookings since MBA-3823; the outbox relay then marks dbo.Booking ATTENDED. The endpoint accepts the
 * AssessmentDelivery BookingId and is idempotent.
 */
@Component
public class AttendanceClient {

    private static final Logger LOG = LoggerFactory.getLogger(AttendanceClient.class);

    private final RestTemplate rest;
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public AttendanceClient(RestTemplateBuilder builder,
                            @Value("${results.scheduling-url:http://localhost:8000}") String baseUrl,
                            @Value("${results.scheduling-timeout-ms:10000}") int timeoutMs) {
        this(builder.setConnectTimeout(timeoutMs).setReadTimeout(timeoutMs).build(), baseUrl);
    }

    public AttendanceClient(RestTemplate rest, String baseUrl) {
        this.rest = rest;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** @return the booking status scheduling-service holds afterwards (ATTENDED) */
    public String recordAttendance(int bookingId) {
        try {
            Map<?, ?> booking = rest.postForObject(baseUrl + "/api/v2/bookings/{id}/attendance", null, Map.class, bookingId);
            return booking == null ? null : String.valueOf(booking.get("status"));
        } catch (HttpStatusCodeException e) {
            String error = "SCHEDULING_ERROR";
            String message = e.getStatusText();
            try {
                Map<?, ?> m = mapper.readValue(e.getResponseBodyAsString(), Map.class);
                error = m.get("error") != null ? String.valueOf(m.get("error")) : error;
                message = m.get("message") != null ? String.valueOf(m.get("message")) : message;
            } catch (IOException | RuntimeException ignored) {
                // non-JSON body
            }
            throw new ApiException(e.getStatusCode().is5xxServerError() ? HttpStatus.BAD_GATEWAY : e.getStatusCode(),
                    error, message);
        } catch (ResourceAccessException e) {
            LOG.error("scheduling-service unreachable at {}: {}", baseUrl, e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SCHEDULING_UNAVAILABLE",
                    "Attendance is recorded by scheduling-service, which is not reachable");
        }
    }
}
