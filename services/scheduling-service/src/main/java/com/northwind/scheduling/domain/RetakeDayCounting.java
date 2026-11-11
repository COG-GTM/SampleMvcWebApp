package com.northwind.scheduling.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Locale;

/**
 * How Exam.RetakeWaitDays is counted (ODD-04, HB-03):
 * <ul>
 * <li>{@link #CALENDAR}: lastAttempt.AddDays(n) - AssessmentDelivery.Business/Rules/RetakeRules.cs (portal).
 * ADR 0002 default.</li>
 * <li>{@link #WORKING}: n Mon-Fri days after the last attempt - booking-api DateUtils.addWorkingDays.</li>
 * </ul>
 */
public enum RetakeDayCounting {
    CALENDAR, WORKING;

    public static RetakeDayCounting parse(String value) {
        if (value == null || value.isBlank()) {
            return CALENDAR;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public LocalDate earliestRetake(LocalDate lastAttempt, int waitDays) {
        if (this == CALENDAR) {
            return lastAttempt.plusDays(waitDays);
        }
        LocalDate d = lastAttempt;
        int added = 0;
        while (added < waitDays) {
            d = d.plusDays(1);
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return d;
    }
}
