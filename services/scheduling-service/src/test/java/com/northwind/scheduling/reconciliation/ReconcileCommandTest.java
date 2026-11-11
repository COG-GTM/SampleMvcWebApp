package com.northwind.scheduling.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReconcileCommandTest {

    @Test
    void readOnlyOverridesAreCommandLineArgsSoTheyBeatApplicationYml() {
        String[] out = ReconcileCommand.commandLine(new String[] {"reconcile", "--scheduling.reconciliation.report-dir=out"});
        assertThat(out).containsExactly("--scheduling.relay.enabled=false", "--scheduling.import.on-startup=false",
                "--spring.main.banner-mode=off", "--scheduling.reconciliation.report-dir=out");
    }

    @Test
    void userArgsAfterTheVerbCanStillOverride() {
        String[] out = ReconcileCommand.commandLine(new String[] {"reconcile"});
        assertThat(out).hasSize(3);
    }
}
