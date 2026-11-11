package com.northwind.assessment.scheduling.domain;

public class SchedulingRuleException extends RuntimeException {

    private final RuleViolation violation;
    private final Integer legacyCode;

    public SchedulingRuleException(RuleViolation violation, String message) {
        this(violation, message, violation.legacyCode());
    }

    public SchedulingRuleException(RuleViolation violation, String message, Integer legacyCode) {
        super(message);
        this.violation = violation;
        this.legacyCode = legacyCode;
    }

    public RuleViolation violation() {
        return violation;
    }

    public Integer legacyCode() {
        return legacyCode;
    }
}
