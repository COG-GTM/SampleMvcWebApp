package com.northwind.ad.results.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.northwind.ad.results.web.ApiException;

/**
 * Marks a booking ATTENDED once its result is stored.
 *
 * MBA-3823: scheduling-service is the single writer of booking status, so attendance goes to
 * {@code POST /api/v2/bookings/{bookingId}/attendance}; its outbox relay then projects ATTENDED into
 * dbo.Booking (results-service's login is denied UPDATE on dbo.Booking by db/security/040_single_writer.sql).
 * With {@code results.scheduling.base-url} empty the legacy direct UPDATE is used, which only works on a
 * database without 040.
 */
@Component
public class AttendanceRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(AttendanceRecorder.class);

    private final JdbcTemplate jdbc;
    private final String baseUrl;
    private final RestTemplate rest;

    @Autowired
    public AttendanceRecorder(JdbcTemplate jdbc,
                              @Value("${results.scheduling.base-url:}") String baseUrl,
                              @Value("${results.scheduling.timeout-ms:5000}") int timeoutMs) {
        this(jdbc, baseUrl, restTemplate(timeoutMs));
    }

    AttendanceRecorder(JdbcTemplate jdbc, String baseUrl, RestTemplate rest) {
        this.jdbc = jdbc;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.rest = rest;
    }

    public boolean usesSchedulingService() {
        return !baseUrl.isEmpty();
    }

    public void recordAttended(int bookingId) {
        if (!usesSchedulingService()) {
            jdbc.update("UPDATE dbo.Booking SET Status = 'ATTENDED', LastModified = GETDATE() "
                    + "WHERE BookingId = ? AND Status = 'BOOKED'", bookingId);
            return;
        }
        String url = baseUrl + "/api/v2/bookings/" + bookingId + "/attendance";
        try {
            rest.postForEntity(url, null, String.class);
            LOG.info("Attendance for booking {} recorded by scheduling-service", bookingId);
        } catch (HttpStatusCodeException e) {
            HttpStatus status = e.getStatusCode();
            if (status == HttpStatus.NOT_FOUND) {
                throw new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND",
                        "Booking " + bookingId + " not found in scheduling-service");
            }
            if (status == HttpStatus.CONFLICT) {
                throw new ApiException(HttpStatus.CONFLICT, "BOOKING_NOT_ACTIVE",
                        "scheduling-service refused attendance for booking " + bookingId + ": "
                                + e.getResponseBodyAsString());
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SCHEDULING_UNAVAILABLE",
                    "scheduling-service returned " + status.value() + " for booking " + bookingId);
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SCHEDULING_UNAVAILABLE",
                    "scheduling-service unreachable at " + baseUrl + ": " + e.getMessage());
        }
    }

    private static RestTemplate restTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(timeoutMs);
        f.setReadTimeout(timeoutMs);
        return new RestTemplate(f);
    }
}
