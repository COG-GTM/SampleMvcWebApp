package com.northwind.assessment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.northwind.assessment.gateway.routing.AllowListHolder;

class StartupGuardTest {

    private static ConfigurableApplicationContext run(String... args) {
        String[] all = new String[args.length + 2];
        all[0] = "--server.port=0";
        all[1] = "--routing.config-file=target/does-not-exist.properties";
        System.arraycopy(args, 0, all, 2, args.length);
        return new SpringApplicationBuilder(RoutingGatewayApplication.class).run(all);
    }

    @Test
    void refusesToStartWithProductionCenterUnlessRelay() {
        assertThatThrownBy(() -> run("--routing.modern-centers=BOS01,NYC01").close())
                .hasStackTraceContaining("Refusing to start")
                .hasStackTraceContaining("routing.coexistence-mode=relay");
    }

    @Test
    void startsWithTestCentersOnly() {
        try (ConfigurableApplicationContext ctx = run("--routing.modern-centers=bos01, akl01")) {
            assertThat(ctx.getBean(AllowListHolder.class).get().modernCenters()).containsExactlyInAnyOrder("BOS01", "AKL01");
        }
    }

    @Test
    void startsWithProductionCenterInRelayMode() {
        try (ConfigurableApplicationContext ctx = run("--routing.modern-centers=NYC01",
                "--routing.coexistence-mode=relay")) {
            assertThat(ctx.getBean(AllowListHolder.class).get().modernCenters()).containsExactly("NYC01");
        }
    }
}
