package com.northwind.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.northwind.routing.route.ApiRoutes;
import com.northwind.routing.route.Lookup;
import com.northwind.routing.route.PortalRoutes;
import com.northwind.routing.route.RouteDecider;
import com.northwind.routing.route.RouteDecision;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class RouteDeciderTest {

    private static final long FLOOR = 50_000_000L;

    private static MultiValueMap<String, String> q(String... kv) {
        MultiValueMap<String, String> m = new LinkedMultiValueMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.add(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void allowListedCentreGoesModernOthersLegacy() {
        assertThat(RouteDecider.decide(Set.of("BOS01"), "bos01", null, FLOOR))
                .isEqualTo(RouteDecision.modern("BOS01", "allow-listed"));
        assertThat(RouteDecider.decide(Set.of("BOS01"), "LON01", null, FLOOR).target()).isEqualTo(RouteDecision.Target.LEGACY);
        assertThat(RouteDecider.decide(Set.of("BOS01"), null, null, FLOOR).target()).isEqualTo(RouteDecision.Target.LEGACY);
        assertThat(RouteDecider.decide(Set.of(), "BOS01", null, FLOOR).target()).isEqualTo(RouteDecision.Target.LEGACY);
    }

    @Test
    void modernBookingIdsStayModernWhateverTheAllowList() {
        assertThat(RouteDecider.decide(Set.of(), "BOS01", FLOOR, FLOOR).modern()).isTrue();
        assertThat(RouteDecider.decide(Set.of(), null, FLOOR + 7, FLOOR).modern()).isTrue();
        assertThat(RouteDecider.decide(Set.of(), "BOS01", FLOOR - 1, FLOOR).modern()).isFalse();
        assertThat(RouteDecider.decide(Set.of("BOS01"), "BOS01", 3500L, FLOOR).modern()).isTrue();
    }

    @Test
    void portalRoutesMapToModernUiDeepLinks() {
        var find = PortalRoutes.match("GET", "/Booking", q("ExamCode", "AZ-104", "CenterCode", "BOS01", "From", "2026-11-10")).orElseThrow();
        assertThat(find.lookup()).isEqualTo(Lookup.of(Lookup.Kind.CENTER_CODE, "BOS01"));
        assertThat(PortalRoutes.redirect(find, "BOS01", q("ExamCode", "AZ-104", "CenterCode", "BOS01", "From", "2026-11-10")))
                .isEqualTo("/app/?center=BOS01&exam=AZ-104&from=2026-11-10");

        var book = PortalRoutes.match("GET", "/booking/book", q("sessionId", "154", "candidateNumber", "NW00000901")).orElseThrow();
        assertThat(PortalRoutes.redirect(book, "BOS01", q())).isEqualTo("/app/book?sessionId=154");
        assertThat(PortalRoutes.match("POST", "/Booking/Book", q()).orElseThrow().needsForm()).isTrue();

        var cancel = PortalRoutes.match("POST", "/Booking/Cancel/50000001", q()).orElseThrow();
        assertThat(cancel.lookup().bookingId()).isEqualTo(50_000_001L);
        assertThat(PortalRoutes.redirect(cancel, "BOS01", q())).isEqualTo("/app/my-bookings");
        var resched = PortalRoutes.match("GET", "/Booking/Reschedule/42/", q()).orElseThrow();
        assertThat(PortalRoutes.redirect(resched, "BOS01", q())).isEqualTo("/app/reschedule/42");
        var mine = PortalRoutes.match("GET", "/Booking/MyBookings", q("centerCode", "BOS01")).orElseThrow();
        assertThat(PortalRoutes.redirect(mine, "BOS01", q())).isEqualTo("/app/my-bookings");
        var admin = PortalRoutes.match("GET", "/Admin/Sessions", q("centerId", "1")).orElseThrow();
        assertThat(admin.lookup()).isEqualTo(Lookup.of(Lookup.Kind.CENTER_ID, "1"));
        assertThat(PortalRoutes.redirect(admin, "BOS01", q())).isEqualTo("/app/admin/centers/BOS01");
        var cap = PortalRoutes.match("GET", "/Admin/Sessions/Capacity/154", q()).orElseThrow();
        assertThat(PortalRoutes.redirect(cap, "BOS01", q())).isEqualTo("/app/admin/sessions/154");
    }

    @Test
    void routesWithoutACentreKeyAndNonBookingPagesStayLegacy() {
        assertThat(PortalRoutes.match("GET", "/Booking", q())).isEmpty();
        assertThat(PortalRoutes.match("GET", "/Booking/MyBookings", q("candidateNumber", "NW00000001"))).isEmpty();
        assertThat(PortalRoutes.match("GET", "/Admin/Sessions", q())).isEmpty();
        assertThat(PortalRoutes.match("GET", "/Admin/Centers", q())).isEmpty();
        assertThat(PortalRoutes.match("GET", "/Admin/Sessions/Roster/154", q("format", "csv"))).isEmpty();
        assertThat(PortalRoutes.match("GET", "/Eligibility", q())).isEmpty();
        assertThat(PortalRoutes.match("GET", "/Results", q())).isEmpty();
        assertThat(PortalRoutes.match("GET", "/Booking/Cancel/abc", q())).isEmpty();
    }

    @Test
    void bookingApiRoutes() {
        assertThat(ApiRoutes.matchV1("GET", "/api/v1/sessions", q("centerCode", "BOS01")).orElseThrow().op()).isEqualTo(ApiRoutes.V1Op.SESSIONS);
        assertThat(ApiRoutes.matchV1("GET", "/api/v1/sessions", q("examCode", "AZ-104"))).isEmpty();
        assertThat(ApiRoutes.matchV1("GET", "/api/v1/sessions/154", q()).orElseThrow().lookup()).isEqualTo(Lookup.of(Lookup.Kind.SESSION_ID, "154"));
        assertThat(ApiRoutes.matchV1("POST", "/api/v1/bookings", q()).orElseThrow().needsBody()).isTrue();
        assertThat(ApiRoutes.matchV1("DELETE", "/api/v1/bookings/9", q()).orElseThrow().op()).isEqualTo(ApiRoutes.V1Op.CANCEL);
        assertThat(ApiRoutes.matchV1("POST", "/api/v1/bookings/9/reschedule", q()).orElseThrow().op()).isEqualTo(ApiRoutes.V1Op.RESCHEDULE);
        assertThat(ApiRoutes.matchV1("GET", "/api/v1/candidates/NW00000001/bookings", q())).isEmpty();
        assertThat(ApiRoutes.matchV2Write("POST", "/api/v2/bookings").orElseThrow().op()).isEqualTo(ApiRoutes.V2Op.BOOK);
        assertThat(ApiRoutes.matchV2Write("PUT", "/api/v2/sessions/154/capacity").orElseThrow().id()).isEqualTo("154");
        assertThat(ApiRoutes.matchV2Write("GET", "/api/v2/bookings/9")).isEmpty();
    }
}
