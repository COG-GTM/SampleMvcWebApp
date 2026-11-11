package com.northwind.scheduling.reconciliation;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.northwind.scheduling.outbox.OutboxRepository;

/**
 * Operator endpoints (MBA-3823), outside /api/v2 and the public OpenAPI spec; the routing gateway does not expose
 * them, call scheduling-service on :8090 directly.
 */
@RestController
public class ReconciliationController {

    private final ReconciliationService reconciliation;
    private final OutboxRepository outbox;

    public ReconciliationController(ReconciliationService reconciliation, OutboxRepository outbox) {
        this.reconciliation = reconciliation;
        this.outbox = outbox;
    }

    /** Compares Scheduling with AssessmentDelivery; write=true also writes reports/reconciliation-&lt;ts&gt;.md */
    @GetMapping("/internal/reconciliation")
    public Reconciliation reconcile(@RequestParam(defaultValue = "true") boolean write) {
        return reconciliation.reconcile(write);
    }

    /** Outbox backlog: total / pending events, last and last relayed sequence, oldest pending timestamp. */
    @GetMapping("/internal/relay")
    public Map<String, Object> relay() {
        return outbox.stats();
    }
}
