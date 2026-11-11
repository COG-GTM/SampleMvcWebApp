package com.northwind.assessment.booking.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import org.junit.Test;

import com.northwind.assessment.booking.model.EligibilityResult;
import com.northwind.assessment.booking.model.ExamAttempt;
import com.northwind.assessment.booking.util.DateUtils;

public class RetakeEligibilityServiceTest {

    private final RetakeEligibilityService service = new RetakeEligibilityService();

    @Test
    public void firstTimeCandidateIsEligible() {
        EligibilityResult r = service.evaluate(new ArrayList<ExamAttempt>(), 30, DateUtils.parseIsoDate("2016-05-02"));
        assertTrue(r.isEligible());
        assertEquals(RetakeEligibilityService.REASON_FIRST_ATTEMPT, r.getReason());
        assertNull(r.getEarliestRetakeDate());
    }

    @Test
    public void passedResultIsNeverEligible() {
        ExamAttempt passed = attempt(DateUtils.parseIsoDate("2014-01-15"), true);
        EligibilityResult r = service.evaluate(Arrays.asList(passed), 30, DateUtils.parseIsoDate("2016-05-02"));
        assertFalse(r.isEligible());
        assertEquals(RetakeEligibilityService.REASON_ALREADY_PASSED, r.getReason());
    }

    private static ExamAttempt attempt(Date sessionDate, boolean passed) {
        ExamAttempt a = new ExamAttempt();
        a.setSessionDate(sessionDate);
        a.setPassed(passed);
        a.setScore(passed ? 800 : 400);
        return a;
    }
}
