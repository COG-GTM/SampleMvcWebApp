package com.northwind.assessment.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import com.northwind.assessment.gateway.resolve.ResolveKey;

class RetiredPortalLinksTest {

    private static RetiredPortalLinks.Target match(String path, Map<String, String> params) {
        return RetiredPortalLinks.match(path, params).orElseThrow();
    }

    @Test
    void myBookingsBookmarkKeepsTheCandidate() {
        RetiredPortalLinks.Target t = match("/booking/mybookings", Map.of("candidatenumber", "NW00000042"));
        assertThat(t.location().apply(null)).isEqualTo("/app/my-bookings?candidateNumber=NW00000042");
        assertThat(t.key()).isNull();
    }

    @Test
    void searchPageCarriesCenterExamAndDateAndUsesTheResolvedCenter() {
        RetiredPortalLinks.Target t = match("/booking",
                Map.of("centercode", "den01", "examcode", "CTA-NET1", "from", "2026-12-01"));
        assertThat(t.key()).isEqualTo(ResolveKey.centerCode("DEN01"));
        assertThat(t.centerHint()).isEqualTo("DEN01");
        assertThat(t.location().apply("DEN01")).isEqualTo("/app?centerCode=DEN01&examCode=CTA-NET1&date=2026-12-01");
    }

    @Test
    void searchRangeDoesNotCollapseToItsFirstDay() {
        assertThat(match("/booking", Map.of("centercode", "den01", "from", "2026-12-01", "to", "2026-12-20"))
                .location().apply("DEN01")).isEqualTo("/app?centerCode=DEN01");
        assertThat(match("/booking", Map.of("centercode", "den01", "from", "2026-12-01", "to", "2026-12-01"))
                .location().apply("DEN01")).isEqualTo("/app?centerCode=DEN01&date=2026-12-01");
    }

    @Test
    void searchPageWithoutParametersGoesToTheModernSearch() {
        assertThat(match("/booking", Map.of()).location().apply(null)).isEqualTo("/app");
        assertThat(match("/booking/index/", Map.of()).location().apply(null)).isEqualTo("/app");
    }

    @ParameterizedTest
    @CsvSource({
        "/booking/book,          sessionId,  700, /app/book?sessionId=700&candidateNumber=NW00000042",
        "/booking/cancel/701,    bookingId,  701, /app/my-bookings?candidateNumber=NW00000042",
        "/booking/reschedule/701, bookingId, 701, /app/reschedule/701?candidateNumber=NW00000042",
        "/admin/sessions/capacity/700, sessionId, 700, /app/admin/sessions/700"})
    void idBasedPagesResolveTheirCenterAndMapToDeepLinks(String path, String keyParam, String id, String location) {
        RetiredPortalLinks.Target t = match(path, Map.of("sessionid", "700", "candidatenumber", "NW00000042"));
        assertThat(t.key()).isEqualTo(new ResolveKey(keyParam, id));
        assertThat(t.location().apply("DEN01")).isEqualTo(location);
    }

    @Test
    void anyOtherBookingUrlFallsBackToTheModernSearch() {
        assertThat(match("/booking/book", Map.of("candidatenumber", "NW00000042")).location().apply(null))
                .isEqualTo("/app?candidateNumber=NW00000042");
        assertThat(match("/booking/cancel", Map.of()).location().apply(null)).isEqualTo("/app");
        assertThat(match("/booking/whatever/1", Map.of("centercode", "NYC02")).location().apply("NYC02"))
                .isEqualTo("/app?centerCode=NYC02");
    }

    @Test
    void valuesAreUrlEncoded() {
        assertThat(match("/booking/mybookings", Map.of("candidatenumber", "NW 1&x=2")).location().apply(null))
                .isEqualTo("/app/my-bookings?candidateNumber=NW%201%26x%3D2");
    }

    @Test
    void pagesThatStayInThePortalDoNotMatch() {
        for (String path : new String[] {"/admin/sessions", "/admin/centers", "/eligibility", "/results", "/",
                "/bookings", "/bookingx/book", "/admin/sessions/capacity/abc"}) {
            assertThat(RetiredPortalLinks.match(path, Map.of())).as(path).isEmpty();
        }
    }
}
