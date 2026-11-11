package com.northwind.assessment.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class RouteDeciderTest {

    private final RouteDecider decider = new RouteDecider();
    private static final Set<String> ALLOW = Set.of("BOS01");

    @Test
    void allowListedCenterGoesModern() {
        RouteDecision d = decider.decide("BOS01", ALLOW, null, null);
        assertThat(d.route()).isEqualTo(Route.MODERN);
        assertThat(d.centerCode()).isEqualTo("BOS01");
        assertThat(d.reason()).isEqualTo("allow-listed");
    }

    @Test
    void centerCodeIsMatchedCaseInsensitively() {
        assertThat(decider.decide(" bos01 ", ALLOW, null, null).route()).isEqualTo(Route.MODERN);
    }

    @Test
    void otherCenterGoesLegacy() {
        RouteDecision d = decider.decide("NYC01", ALLOW, null, null);
        assertThat(d.route()).isEqualTo(Route.LEGACY);
        assertThat(d.reason()).isEqualTo("not-allow-listed");
    }

    @Test
    void emptyAllowListGoesLegacy() {
        assertThat(decider.decide("BOS01", Set.of(), null, null).route()).isEqualTo(Route.LEGACY);
        assertThat(decider.decide("BOS01", null, null, null).route()).isEqualTo(Route.LEGACY);
    }

    @Test
    void unknownCenterGoesLegacy() {
        assertThat(decider.decide(null, ALLOW, null, null).reason()).isEqualTo("no-center");
        assertThat(decider.decide("  ", ALLOW, null, null).route()).isEqualTo(Route.LEGACY);
    }

    @Test
    void modernBookingIdAlwaysGoesModernRegardlessOfAllowList() {
        assertThat(decider.decide("NYC01", Set.of(), 50_000_000L, null).route()).isEqualTo(Route.MODERN);
        assertThat(decider.decide(null, Set.of(), 50_000_123L, null).reason()).isEqualTo("modern-booking-id");
    }

    @Test
    void bookingIdJustBelowFloorIsNotModernByItself() {
        assertThat(decider.decide("NYC01", Set.of(), 49_999_999L, null).route()).isEqualTo(Route.LEGACY);
    }

    @Test
    void modernBookingOriginAlwaysGoesModern() {
        RouteDecision d = decider.decide("NYC01", Set.of(), 123L, "MODERN");
        assertThat(d.route()).isEqualTo(Route.MODERN);
        assertThat(d.reason()).isEqualTo("modern-booking-origin");
    }

    @Test
    void legacyImportBookingFollowsAllowList() {
        assertThat(decider.decide("BOS01", ALLOW, 123L, "LEGACY_IMPORT").route()).isEqualTo(Route.MODERN);
        assertThat(decider.decide("NYC01", ALLOW, 123L, "LEGACY_IMPORT").route()).isEqualTo(Route.LEGACY);
    }

    @Test
    void wildcardRoutesEveryResolvedCenterModern() {
        RouteDecision d = decider.decide("NYC01", Set.of("*"), null, null);
        assertThat(d.route()).isEqualTo(Route.MODERN);
        assertThat(d.reason()).isEqualTo("all-centers");
        assertThat(decider.decide("BOS01", Set.of("BOS01", "*"), null, null).reason()).isEqualTo("allow-listed");
        assertThat(decider.decide(null, Set.of("*"), null, null).route()).isEqualTo(Route.LEGACY);
    }

    @Test
    void writeCentersApplyToWritesOnly() {
        AllowList list = new AllowList(Set.of("BOS01"), Set.of("*"), Set.of("BOS01"), "relay");
        assertThat(list.centersFor(false)).containsExactly("BOS01");
        assertThat(list.centersFor(true)).containsExactlyInAnyOrder("BOS01", "*");
        assertThat(new AllowList(Set.of(), Set.of("*"), Set.of(), "relay").isEmpty()).isFalse();
        assertThat(new AllowList(Set.of(), Set.of(), Set.of(), "none").isEmpty()).isTrue();
    }

    @Test
    void customFloor() {
        assertThat(new RouteDecider(1000).isModernBookingId(1000L)).isTrue();
        assertThat(new RouteDecider(1000).isModernBookingId(999L)).isFalse();
        assertThat(new RouteDecider(1000).isModernBookingId(null)).isFalse();
    }

    @Test
    void routeHeaderValues() {
        assertThat(Route.LEGACY.header()).isEqualTo("legacy");
        assertThat(Route.MODERN.header()).isEqualTo("modern");
    }
}
