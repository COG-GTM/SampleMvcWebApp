package com.northwind.ad.results.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * MBA-3823: results-service no longer writes dbo.Booking. A posted result asks scheduling-service to record
 * attendance (stubbed here together with the relay projection), and the service's own login is denied the
 * direct UPDATE it used to run.
 */
public class AttendanceViaSchedulingTest extends AbstractResultsIT {

    @Test
    @SuppressWarnings("rawtypes")
    public void postedResultRecordsAttendanceInSchedulingServiceThenLegacy() {
        Fixture f = newBooking();
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("bookingId", f.bookingId);
        body.put("score", 655);
        ResponseEntity<Map> r = rest.postForEntity("/api/v1/results", body, Map.class);

        assertEquals(HttpStatus.CREATED, r.getStatusCode());
        assertTrue(SchedulingStub.get().attendanceCalls().contains(f.bookingId));
        assertEquals("ATTENDED", booking(f.bookingId).get("Status"));
        assertEquals(1, resultCount(f.bookingId));
    }

    @Test
    public void resultsLoginCannotUpdateBookingStatusDirectly() {
        Fixture f = newBooking();
        try {
            jdbc.update("UPDATE dbo.Booking SET Status = 'ATTENDED' WHERE BookingId = ?", f.bookingId);
            fail("ad_results must be denied UPDATE on dbo.Booking");
        } catch (DataAccessException e) {
            assertTrue(e.getMostSpecificCause().getMessage(),
                    e.getMostSpecificCause().getMessage().contains("permission was denied"));
        }
        assertEquals("BOOKED", booking(f.bookingId).get("Status"));
    }
}
