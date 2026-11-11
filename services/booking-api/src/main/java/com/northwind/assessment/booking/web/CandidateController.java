package com.northwind.assessment.booking.web;

import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.northwind.assessment.booking.dao.BookingDao;
import com.northwind.assessment.booking.dao.CandidateDao;
import com.northwind.assessment.booking.model.BookingInfo;
import com.northwind.assessment.booking.model.Candidate;
import com.northwind.assessment.booking.model.EligibilityResult;
import com.northwind.assessment.booking.service.RetakeEligibilityService;
import com.northwind.assessment.booking.util.DateUtils;

@RestController
@RequestMapping("/api/v1/candidates")
public class CandidateController {

    @Autowired
    private CandidateDao candidateDao;

    @Autowired
    private BookingDao bookingDao;

    @Autowired
    private RetakeEligibilityService retakeEligibilityService;

    @RequestMapping(value = "/{candidateNumber}/bookings", method = RequestMethod.GET)
    public List<BookingInfo> bookings(@PathVariable("candidateNumber") String candidateNumber) {
        Candidate c = load(candidateNumber);
        return bookingDao.findByCandidate(c.getCandidateId());
    }

    @RequestMapping(value = "/{candidateNumber}/eligibility", method = RequestMethod.GET)
    public EligibilityResult eligibility(@PathVariable("candidateNumber") String candidateNumber,
                                         @RequestParam(value = "examCode", required = false) String examCode,
                                         @RequestParam(value = "date", required = false) String date) {
        if (StringUtils.isBlank(examCode)) {
            throw ApiException.badRequest("VALIDATION", "examCode is required");
        }
        Date onDate;
        try {
            onDate = date == null ? DateUtils.today() : DateUtils.parseIsoDate(date);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VALIDATION", e.getMessage());
        }
        Candidate c = load(candidateNumber);
        Integer examId = candidateDao.findExamIdByCode(examCode);
        if (examId == null) {
            throw ApiException.notFound("EXAM_NOT_FOUND", "Exam " + examCode + " not found");
        }
        return retakeEligibilityService.check(c.getCandidateId(), examId.intValue(), onDate);
    }

    private Candidate load(String candidateNumber) {
        Candidate c = candidateDao.findByNumber(candidateNumber);
        if (c == null) {
            throw ApiException.notFound("CANDIDATE_NOT_FOUND", "Candidate " + candidateNumber + " not found");
        }
        return c;
    }
}
