package com.northwind.assessment.booking.service;

import java.util.Date;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.northwind.assessment.booking.model.EligibilityResult;
import com.northwind.assessment.booking.util.DateUtils;
import com.northwind.assessment.booking.web.ApiException;

/**
 * Partner retake rule, now evaluated by scheduling-service ({@code GET /eligibility/retake?policy=PARTNER_WORKING_DAYS},
 * docs/to-be/adr-001-retake-eligibility-and-attendance.md). The rule itself (working days from the first attempt
 * ordered StartsAt DESC, ResultId DESC; reason codes below) moved there unchanged (HB-07). booking-api keeps only the
 * Candidate/Exam reference lookups it already owned.
 */
@Service
public class RetakeEligibilityService {

    public static final String REASON_FIRST_ATTEMPT = "FIRST_ATTEMPT";
    public static final String REASON_ALREADY_PASSED = "ALREADY_PASSED";
    public static final String REASON_RETAKE_WAIT = "RETAKE_WAIT_NOT_ELAPSED";
    public static final String REASON_RETAKE_ALLOWED = "RETAKE_ALLOWED";

    static final String POLICY = "PARTNER_WORKING_DAYS";

    private static final Logger LOG = LoggerFactory.getLogger(RetakeEligibilityService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final RestTemplate rest;

    @Value("${scheduling.base-url:http://localhost:8084/api/v1}")
    private String baseUrl;

    @Autowired
    public RetakeEligibilityService(RestTemplateBuilder builder) {
        this.rest = builder.setConnectTimeout(2000).setReadTimeout(10000).build();
    }

    public EligibilityResult check(int candidateId, int examId, Date onDate) {
        String candidateNumber = jdbcTemplate.queryForObject(
                "SELECT CandidateNumber FROM dbo.Candidate WHERE CandidateId = ?", String.class, candidateId);
        String examCode = jdbcTemplate.queryForObject("SELECT ExamCode FROM dbo.Exam WHERE ExamId = ?",
                String.class, examId);
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/eligibility/retake")
                .queryParam("candidateNumber", candidateNumber)
                .queryParam("examCode", examCode)
                .queryParam("date", DateUtils.formatIsoDate(onDate))
                .queryParam("policy", POLICY)
                .build().encode().toUriString();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> d = rest.getForObject(url, Map.class);
            return new EligibilityResult(Boolean.TRUE.equals(d.get("eligible")), (String) d.get("reason"),
                    (String) d.get("earliestRetakeDate"));
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new ApiException(e.getStatusCode(), "ELIGIBILITY_" + e.getStatusCode().value(),
                        e.getResponseBodyAsString());
            }
            throw unavailable(e);
        } catch (ResourceAccessException e) {
            throw unavailable(e);
        }
    }

    private static ApiException unavailable(Exception e) {
        LOG.warn("scheduling-service unavailable for retake eligibility: {}", e.getMessage());
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SCHEDULING_UNAVAILABLE",
                "Retake eligibility is temporarily unavailable, retry later");
    }
}
