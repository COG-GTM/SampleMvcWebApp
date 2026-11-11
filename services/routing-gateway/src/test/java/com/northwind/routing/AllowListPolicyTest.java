package com.northwind.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.northwind.routing.policy.AllowListPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class AllowListPolicyTest {

    private static final List<String> TEST_CENTERS = List.of("BOS01", "SEA01", "AKL01");

    @Test
    void emptyListIsAlwaysAllowed() {
        assertThat(AllowListPolicy.validate(List.of(), TEST_CENTERS, "none")).isEmpty();
        assertThat(AllowListPolicy.validate(List.of(" ", ""), TEST_CENTERS, null)).isEmpty();
    }

    @Test
    void testCentresMayBeListedBeforeM4AndAreNormalised() {
        assertThat(AllowListPolicy.validate(List.of(" bos01", "SEA01", "BOS01"), TEST_CENTERS, "none"))
                .containsExactlyInAnyOrder("BOS01", "SEA01");
    }

    @Test
    void productionCentreIsRefusedUnlessRelay() {
        assertThatThrownBy(() -> AllowListPolicy.validate(List.of("BOS01", "LON01"), TEST_CENTERS, "none"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("[LON01]").hasMessageContaining("relay");
        assertThat(AllowListPolicy.validate(List.of("LON01"), TEST_CENTERS, "RELAY")).containsExactly("LON01");
    }

    @Test
    void wildcardMeansEveryCentreAndNeedsRelay() {
        assertThatThrownBy(() -> AllowListPolicy.validate(List.of("*"), TEST_CENTERS, "none"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("relay");
        assertThat(AllowListPolicy.validate(List.of(" * "), TEST_CENTERS, "relay")).containsExactly("*");
        assertThat(com.northwind.routing.route.RouteDecider.decide(java.util.Set.of("*"), "lon01", null, 50_000_000L).modern())
                .isTrue();
        assertThat(com.northwind.routing.route.RouteDecider.decide(java.util.Set.of("*"), null, null, 50_000_000L).modern())
                .isFalse();
    }
}
