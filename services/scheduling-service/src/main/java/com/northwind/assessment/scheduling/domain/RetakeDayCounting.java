package com.northwind.assessment.scheduling.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** How the retake wait is counted (ADR 0002, "working vs calendar retake days"). */
public enum RetakeDayCounting {

    /** Portal {@code RetakeRules.cs}: {@code lastAttempt.Date.AddDays(retakeWaitDays)}. Chosen by ADR 0002. */
    CALENDAR {
        @Override
        public LocalDate add(LocalDate from, int days) {
            return from.plusDays(days);
        }
    },

    /**
     * booking-api {@code DateUtils.addWorkingDays}: step one day at a time, counting only Monday-Friday
     * (no holiday calendar). Zero days returns {@code from} even on a weekend.
     */
    WORKING_DAYS {
        @Override
        public LocalDate add(LocalDate from, int days) {
            LocalDate d = from;
            int added = 0;
            while (added < days) {
                d = d.plusDays(1);
                if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    added++;
                }
            }
            return d;
        }
    };

    public abstract LocalDate add(LocalDate from, int days);
}
