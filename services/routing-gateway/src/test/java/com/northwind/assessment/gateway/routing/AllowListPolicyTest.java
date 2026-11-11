package com.northwind.assessment.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.Test;

class AllowListPolicyTest {

    private static final Set<String> TEST = Set.of("BOS01", "SEA01", "AKL01");

    @Test
    void normalizeTrimsUpperCasesAndDropsBlanks() {
        assertThat(AllowListPolicy.normalize(Arrays.asList(" bos01", "", null, "SEA01 ", "bos01")))
                .containsExactly("BOS01", "SEA01");
    }

    @Test
    void emptyListIsAlwaysAccepted() {
        assertThat(AllowListPolicy.violations(Set.of(), TEST, "none")).isEmpty();
    }

    @Test
    void testCentersAreAcceptedWithoutRelay() {
        assertThat(AllowListPolicy.violations(Set.of("BOS01", "AKL01"), TEST, "none")).isEmpty();
    }

    @Test
    void productionCenterRequiresRelay() {
        assertThat(AllowListPolicy.violations(Set.of("BOS01", "NYC01"), TEST, "none"))
                .singleElement().asString().contains("[NYC01]").contains("routing.coexistence-mode=relay");
        assertThat(AllowListPolicy.violations(Set.of("NYC01"), TEST, null)).hasSize(1);
    }

    @Test
    void wildcardRequiresRelay() {
        assertThat(AllowListPolicy.violations(Set.of("*"), TEST, "none")).singleElement().asString().contains("[*]");
        assertThat(AllowListPolicy.violations(Set.of("*"), TEST, "relay")).isEmpty();
    }

    @Test
    void relayModeLiftsTheRestriction() {
        assertThat(AllowListPolicy.violations(Set.of("NYC01"), TEST, " RELAY ")).isEmpty();
    }
}
