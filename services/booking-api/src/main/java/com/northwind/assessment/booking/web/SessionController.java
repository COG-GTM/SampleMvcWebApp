package com.northwind.assessment.booking.web;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.northwind.assessment.booking.dao.SessionDao;
import com.northwind.assessment.booking.model.SessionInfo;
import com.northwind.assessment.booking.util.DateUtils;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    @Autowired
    private SessionDao sessionDao;

    @RequestMapping(method = RequestMethod.GET)
    public List<SessionInfo> list(@RequestParam(value = "examCode", required = false) String examCode,
                                  @RequestParam(value = "centerCode", required = false) String centerCode,
                                  @RequestParam(value = "from", required = false) String from,
                                  @RequestParam(value = "to", required = false) String to) {
        Date fromDate;
        Date toDate;
        try {
            fromDate = DateUtils.parseIsoDate(from);
            toDate = DateUtils.parseIsoDate(to);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VALIDATION", e.getMessage());
        }
        return sessionDao.find(examCode, centerCode, fromDate, toDate);
    }

    @RequestMapping(value = "/{sessionId}", method = RequestMethod.GET)
    public SessionInfo get(@PathVariable("sessionId") int sessionId) {
        SessionInfo s = sessionDao.findById(sessionId);
        if (s == null) {
            throw ApiException.notFound("SESSION_NOT_FOUND", "Session " + sessionId + " not found");
        }
        s.setBookedSeats(sessionDao.countBookedSeats(sessionId));
        return s;
    }
}
