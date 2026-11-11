package com.northwind.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.northwind.routing.policy.AllowList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** The gateway refuses to start with a non-test centre allow-listed unless routing.coexistence-mode=relay. */
class StartupGuardTest {

    private static ConfigurableApplicationContext start(String... args) {
        String[] all = new String[args.length + 2];
        all[0] = "--server.port=0";
        all[1] = "--routing.config-file=target/no-such-routing.properties";
        System.arraycopy(args, 0, all, 2, args.length);
        return new SpringApplicationBuilder(RoutingGatewayApplication.class).run(all);
    }

    @Test
    void refusesProductionCentreWithoutRelay() {
        assertThatThrownBy(() -> start("--routing.modern-centers=LON01", "--routing.coexistence-mode=none"))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("LON01").hasMessageContaining("coexistence-mode=relay");
    }

    @Test
    void startsWithTestCentreOrWithRelay() {
        try (ConfigurableApplicationContext ctx = start("--routing.modern-centers=BOS01,sea01")) {
            assertThat(ctx.getBean(AllowList.class).centers()).containsExactlyInAnyOrder("BOS01", "SEA01");
        }
        try (ConfigurableApplicationContext ctx = start("--routing.modern-centers=LON01", "--routing.coexistence-mode=relay")) {
            assertThat(ctx.getBean(AllowList.class).centers()).containsExactly("LON01");
        }
    }
}
