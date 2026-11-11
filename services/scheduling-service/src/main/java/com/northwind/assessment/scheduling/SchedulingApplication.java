package com.northwind.assessment.scheduling;

import com.northwind.assessment.scheduling.reconciliation.ReconcileCommand;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SchedulingApplication {

    public static void main(String[] args) {
        if (args.length > 0 && "reconcile".equals(args[0])) {
            System.exit(reconcile(Arrays.copyOfRange(args, 1, args.length)));
        }
        SpringApplication.run(SchedulingApplication.class, args);
    }

    /**
     * CLI {@code reconcile}: no web server, no relay, no import; waits for the running service's relay to drain
     * the outbox ({@code --wait-seconds}, default 30), writes reports/reconciliation-&lt;timestamp&gt;.md and exits
     * 0 when there are no mismatches and no seat drift, 1 otherwise.
     */
    static int reconcile(String[] args) {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(SchedulingApplication.class)
                .web(WebApplicationType.NONE)
                .properties("scheduling.relay.enabled=false", "scheduling.import.on-startup=false",
                        "spring.main.banner-mode=off")
                .run(args);
        try {
            return ctx.getBean(ReconcileCommand.class).run();
        } finally {
            ctx.close();
        }
    }
}
