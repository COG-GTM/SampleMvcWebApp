package com.northwind.routing.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InternalPathTest {

    @Test
    void internalEndpointsAreRecognisedInAnySpelling() {
        assertThat(StranglerFilter.internalPath("/api/v2/internal/import/sessions")).isTrue();
        assertThat(StranglerFilter.internalPath("/api/v2//Internal/routing/resolve")).isTrue();
        assertThat(StranglerFilter.internalPath("/api/v2/%69nternal/routing/resolve")).isTrue();
        assertThat(StranglerFilter.internalPath("/api/v2;x=1/internal/import/sessions")).isTrue();
        assertThat(StranglerFilter.internalPath("/api/v2/centers/BOS01/sessions")).isFalse();
        assertThat(StranglerFilter.internalPath("/api/v2/bookings")).isFalse();
    }

    @Test
    void attendanceIsRecognisedInAnySpelling() {
        assertThat(StranglerFilter.attendancePath("/api/v2/bookings/50000007/attendance")).isTrue();
        assertThat(StranglerFilter.attendancePath("/api/v2//bookings/7/Attendance/")).isTrue();
        assertThat(StranglerFilter.attendancePath("/api/v2/bookings/7/%61ttendance")).isTrue();
        assertThat(StranglerFilter.attendancePath("/api/v2/bookings;x=1/7/attendance")).isTrue();
        assertThat(StranglerFilter.attendancePath("/api/v2/bookings/7")).isFalse();
        assertThat(StranglerFilter.attendancePath("/api/v2/bookings/7/reschedule")).isFalse();
    }
}
