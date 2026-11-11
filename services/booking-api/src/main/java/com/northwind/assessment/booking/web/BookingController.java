package com.northwind.assessment.booking.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.northwind.assessment.booking.dao.BookingDao;
import com.northwind.assessment.booking.model.BookingInfo;

/**
 * Partner booking reads. MBA-3824 retired the write SQL: book, cancel and reschedule are served by
 * scheduling-service through the routing gateway (:8000), which answers /api/v1/bookings writes itself
 * (docs/adr/0004-retire-portal-booking-slice.md). A write that reaches :8081 directly gets 410.
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    static final String LEGACY_WRITE_RETIRED = "LEGACY_WRITE_RETIRED";

    @Autowired
    private BookingDao bookingDao;

    @RequestMapping(method = RequestMethod.POST)
    public void book() {
        throw retired();
    }

    @RequestMapping(value = "/{bookingId}", method = RequestMethod.GET)
    public BookingInfo get(@PathVariable("bookingId") int bookingId) {
        BookingInfo b = bookingDao.findById(bookingId);
        if (b == null) {
            throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking " + bookingId + " not found");
        }
        return b;
    }

    @RequestMapping(value = "/{bookingId}", method = RequestMethod.DELETE)
    public void cancel(@PathVariable("bookingId") int bookingId) {
        throw retired();
    }

    @RequestMapping(value = "/{bookingId}/reschedule", method = RequestMethod.POST)
    public void reschedule(@PathVariable("bookingId") int bookingId) {
        throw retired();
    }

    static ApiException retired() {
        return new ApiException(HttpStatus.GONE, LEGACY_WRITE_RETIRED, "booking-api no longer writes bookings; "
                + "send book/cancel/reschedule through the routing gateway (:8000) to scheduling-service");
    }
}
