package com.northwind.scheduling.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.northwind.scheduling.domain.ErrorCode;
import com.northwind.scheduling.domain.SchedulingException;

class AttendanceTokenGuardTest {

    @Test
    void acceptsOnlyTheConfiguredToken() {
        AttendanceTokenGuard g = new AttendanceTokenGuard("s3cret");
        assertThatCode(() -> g.check("s3cret")).doesNotThrowAnyException();
        assertUnauthorized(g, null);
        assertUnauthorized(g, "");
        assertUnauthorized(g, "s3cre");
        assertUnauthorized(g, "s3cret ");
    }

    @Test
    void failsClosedWhenNoTokenIsConfigured() {
        assertUnauthorized(new AttendanceTokenGuard(""), "");
        assertUnauthorized(new AttendanceTokenGuard(null), "anything");
        assertUnauthorized(new AttendanceTokenGuard("  "), "  ");
    }

    private static void assertUnauthorized(AttendanceTokenGuard g, String token) {
        assertThatThrownBy(() -> g.check(token)).isInstanceOf(SchedulingException.class)
                .extracting(e -> ((SchedulingException) e).code()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
