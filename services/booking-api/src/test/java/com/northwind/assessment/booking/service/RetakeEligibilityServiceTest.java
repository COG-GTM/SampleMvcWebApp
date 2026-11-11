package com.northwind.assessment.booking.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.northwind.assessment.booking.model.EligibilityResult;
import com.northwind.assessment.booking.util.DateUtils;
import com.northwind.assessment.booking.web.ApiException;

/** The partner rule itself is pinned in scheduling-service and RetakeEligibilityContractTest; this pins the call. */
public class RetakeEligibilityServiceTest {

    private RetakeEligibilityService service;
    private MockRestServiceServer server;

    @Before
    public void setUp() {
        service = new RetakeEligibilityService(new RestTemplateBuilder());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(startsWith("SELECT CandidateNumber"), eq(String.class), eq(7))).thenReturn("NW00000007");
        when(jdbc.queryForObject(startsWith("SELECT ExamCode"), eq(String.class), eq(3))).thenReturn("AZ-104");
        when(jdbc.queryForObject(startsWith("SELECT ExamCode"), eq(String.class), eq(4))).thenReturn("C++ 1");
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(service, "baseUrl", "http://sched/api/v1");
        server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(service, "rest")).build();
    }

    @Test
    public void asksSchedulingServiceWithThePartnerPolicyAndMapsTheDecision() {
        server.expect(requestTo("http://sched/api/v1/eligibility/retake?candidateNumber=NW00000007&examCode=AZ-104"
                + "&date=2026-12-01&policy=PARTNER_WORKING_DAYS"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"eligible\":false,\"reason\":\"RETAKE_WAIT_NOT_ELAPSED\","
                        + "\"earliestRetakeDate\":\"2026-12-14\",\"policy\":\"PARTNER_WORKING_DAYS\"}",
                        MediaType.APPLICATION_JSON));

        EligibilityResult r = service.check(7, 3, DateUtils.parseIsoDate("2026-12-01"));

        assertFalse(r.isEligible());
        assertEquals(RetakeEligibilityService.REASON_RETAKE_WAIT, r.getReason());
        assertEquals("2026-12-14", r.getEarliestRetakeDate());
        server.verify();
    }

    @Test
    public void reservedCharactersInCodesAreEncoded() {
        server.expect(requestTo("http://sched/api/v1/eligibility/retake?candidateNumber=NW00000007&examCode=C%2B%2B%201"
                + "&date=2026-12-01&policy=PARTNER_WORKING_DAYS"))
                .andRespond(withSuccess("{\"eligible\":true,\"reason\":\"FIRST_ATTEMPT\",\"earliestRetakeDate\":null}",
                        MediaType.APPLICATION_JSON));

        assertTrue(service.check(7, 4, DateUtils.parseIsoDate("2026-12-01")).isEligible());
        server.verify();
    }

    @Test
    public void firstAttemptHasNoEarliestDate() {
        server.expect(requestTo(Matchers.startsWith("http://sched/api/v1/eligibility/retake")))
                .andRespond(withSuccess("{\"eligible\":true,\"reason\":\"FIRST_ATTEMPT\",\"earliestRetakeDate\":null,"
                        + "\"policy\":\"PARTNER_WORKING_DAYS\"}", MediaType.APPLICATION_JSON));

        EligibilityResult r = service.check(7, 3, DateUtils.parseIsoDate("2026-12-01"));

        assertTrue(r.isEligible());
        assertEquals(RetakeEligibilityService.REASON_FIRST_ATTEMPT, r.getReason());
        assertNull(r.getEarliestRetakeDate());
    }

    @Test
    public void schedulingServiceDownIs503() {
        server.expect(requestTo(Matchers.startsWith("http://sched/api/v1/eligibility/retake")))
                .andRespond(withServerError());
        try {
            service.check(7, 3, DateUtils.parseIsoDate("2026-12-01"));
            fail();
        } catch (ApiException e) {
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        }
    }
}
