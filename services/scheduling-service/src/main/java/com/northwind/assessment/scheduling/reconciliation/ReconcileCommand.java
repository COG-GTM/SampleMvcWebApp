package com.northwind.assessment.scheduling.reconciliation;

import java.io.PrintStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** {@code java -jar scheduling-service.jar reconcile [--wait-seconds=30]} (see SchedulingApplication). */
@Component
public class ReconcileCommand {

    private final ReconciliationService service;
    private final long waitSeconds;

    public ReconcileCommand(ReconciliationService service, @Value("${wait-seconds:30}") long waitSeconds) {
        this.service = service;
        this.waitSeconds = waitSeconds;
    }

    public int run() {
        return run(System.out);
    }

    int run(PrintStream out) {
        long deadline = System.currentTimeMillis() + waitSeconds * 1000;
        while (service.relayStatus().pendingEvents() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        ReconciliationService.Report r = service.reconcile(true);
        out.printf("reconcile: sessions=%d bookings=%d mismatches=%d seat-drift-rows=%d pending-events=%d p95-lag-ms=%s report=%s%n",
                r.sessionsCompared(), r.bookingsCompared(), r.mismatchCount(), r.seatDriftRows().size(),
                r.relay().pendingEvents(), r.relay().p95LagMs(), r.reportFile());
        return r.mismatchCount() == 0 && r.seatDriftRows().isEmpty() ? 0 : 1;
    }
}
