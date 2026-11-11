package com.northwind.assessment.booking.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.springframework.http.HttpStatus;

/** MBA-3824: booking-api no longer writes; direct writes on :8081 are answered 410 LEGACY_WRITE_RETIRED. */
public class BookingControllerTest {

    private final BookingController controller = new BookingController();

    @Test
    public void bookIsRetired() {
        assertRetired(new Runnable() { public void run() { controller.book(); } });
    }

    @Test
    public void cancelIsRetired() {
        assertRetired(new Runnable() { public void run() { controller.cancel(42); } });
    }

    @Test
    public void rescheduleIsRetired() {
        assertRetired(new Runnable() { public void run() { controller.reschedule(42); } });
    }

    @Test
    public void retiredWriteRendersAs410() {
        assertEquals(HttpStatus.GONE, new ApiExceptionHandler().handleApi(BookingController.retired()).getStatusCode());
    }

    private static void assertRetired(Runnable call) {
        try {
            call.run();
            fail("expected ApiException");
        } catch (ApiException e) {
            assertEquals(HttpStatus.GONE, e.getStatus());
            assertEquals("LEGACY_WRITE_RETIRED", e.getError());
        }
    }
}
