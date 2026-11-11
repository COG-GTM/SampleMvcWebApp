package com.northwind.scheduling.reconciliation;

import java.util.Arrays;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * CLI {@code java -jar scheduling-service.jar reconcile [--spring.props...]} (scripts/reconcile.ps1|.sh): starts the
 * context without the web server, relay or start-up import, writes reports/reconciliation-&lt;ts&gt;.md and exits
 * 0 when there are no mismatches and seat-drift.sql returned no rows, else 1.
 */
public final class ReconcileCommand {

    private ReconcileCommand() {
    }

    public static int run(Class<?> app, String[] args) {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(app).web(WebApplicationType.NONE)
                .run(commandLine(args))) {
            Reconciliation r = ctx.getBean(ReconciliationService.class).reconcile(true);
            System.out.printf("reconcile: sessions=%d bookings=%d mismatches=%d (session fields %d, bookings %d) "
                            + "seat-drift rows=%d report=%s%n", r.sessionsCompared(), r.bookingsCompared(),
                    r.mismatches(), r.sessionMismatches().size(), r.bookingMismatches().size(), r.seatDrift().size(),
                    r.reportFile());
            return r.mismatches() == 0 && r.seatDrift().isEmpty() ? 0 : 1;
        }
    }

    /** Read-only overrides go on the command line: default properties would lose to application.yml. */
    static String[] commandLine(String[] args) {
        String[] fixed = {"--scheduling.relay.enabled=false", "--scheduling.import.on-startup=false",
                "--spring.main.banner-mode=off"};
        String[] out = Arrays.copyOf(fixed, fixed.length + args.length - 1);
        System.arraycopy(args, 1, out, fixed.length, args.length - 1);
        return out;
    }
}
