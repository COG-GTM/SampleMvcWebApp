package com.northwind.assessment.booking.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.northwind.assessment.booking.dao.BookingDao;
import com.northwind.assessment.booking.model.BookingInfo;
import com.northwind.assessment.booking.model.BookingRequest;
import com.northwind.assessment.booking.model.BookingResponse;
import com.northwind.assessment.booking.model.RescheduleRequest;
import com.northwind.assessment.booking.service.BookingService;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingDao bookingDao;

    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<BookingResponse> book(@RequestBody BookingRequest req) {
        return new ResponseEntity<BookingResponse>(bookingService.book(req), HttpStatus.CREATED);
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
    public BookingInfo cancel(@PathVariable("bookingId") int bookingId,
                              @RequestParam(value = "reason", required = false) String reason) {
        bookingService.cancel(bookingId, reason);
        return bookingDao.findById(bookingId);
    }

    @RequestMapping(value = "/{bookingId}/reschedule", method = RequestMethod.POST)
    public BookingResponse reschedule(@PathVariable("bookingId") int bookingId,
                                      @RequestBody RescheduleRequest req) {
        return bookingService.reschedule(bookingId, req == null ? null : req.getNewSessionId());
    }
}
