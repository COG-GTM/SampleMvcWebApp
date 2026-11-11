package com.northwind.scheduling.web;

import com.northwind.scheduling.api.ApiError;
import com.northwind.scheduling.api.ApiException;
import com.northwind.scheduling.core.ReadModels;
import com.northwind.scheduling.eligibility.RetakeEligibility;
import com.northwind.scheduling.reconciler.SeatReconciler;
import com.northwind.scheduling.reference.LegacyImporter;
import com.northwind.scheduling.reference.RefEntity;
import com.northwind.scheduling.reference.ReferenceData;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Eligibility, reference data, reconciler and audit endpoints. */
@RestController
public class AdminControllers {

    private final RetakeEligibility eligibility;
    private final ReferenceData reference;
    private final SeatReconciler reconciler;
    private final ReadModels reads;
    private final LegacyImporter importer;

    @org.springframework.beans.factory.annotation.Value("${scheduling.import.api-enabled:false}")
    private boolean importApiEnabled;

    public AdminControllers(RetakeEligibility eligibility, ReferenceData reference, SeatReconciler reconciler, ReadModels reads,
                            LegacyImporter importer) {
        this.eligibility = eligibility;
        this.reference = reference;
        this.reconciler = reconciler;
        this.reads = reads;
        this.importer = importer;
    }

    @GetMapping("/api/v1/eligibility/retake")
    public RetakeEligibility.Decision retake(@RequestParam String candidateNumber, @RequestParam String examCode,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                             @RequestParam(required = false) String policy) {
        return eligibility.check(candidateNumber, examCode, date, policy);
    }

    // ------------------------------------------------------------------ reference data

    public record ReferenceUpsert(Long sourceVersion, Boolean deleted, Map<String, Object> data) {
    }

    @PutMapping("/api/v1/reference/{entity}/{id}")
    public ResponseEntity<?> putReference(@PathVariable String entity, @PathVariable int id, @RequestBody ReferenceUpsert body) {
        RefEntity e = entity(entity);
        if (body == null || body.sourceVersion() == null || (body.data() == null && !Boolean.TRUE.equals(body.deleted()))) {
            throw ApiException.badRequest("VALIDATION", "sourceVersion and data are required");
        }
        boolean applied = reference.upsert(e, id, new ReferenceData.Upsert(body.sourceVersion(), Boolean.TRUE.equals(body.deleted()),
                body.data()));
        return applied ? ResponseEntity.ok(reference.get(e, id).orElseThrow()) : ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/reference/{entity}/{id}")
    public ResponseEntity<?> getReference(@PathVariable String entity, @PathVariable int id) {
        RefEntity e = entity(entity);
        return reference.get(e, id).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiError("NOT_FOUND", entity + " " + id + " not replicated", null, null)));
    }

    @GetMapping("/api/v1/reference/status")
    public List<ReferenceData.Status> referenceStatus() {
        return reference.status();
    }

    /** Contract addition: force an immediate relay catch-up (full=true resyncs every entity). */
    @PostMapping("/api/v1/reference/sync")
    public Map<String, Object> sync(@RequestParam(defaultValue = "false") boolean full) {
        int applied = full ? reference.resyncAll() : reference.catchUp();
        return Map.of("applied", applied);
    }

    /** Contract addition: (re-)run the initial import from AssessmentDelivery. replace=true wipes scheduling data first. */
    @PostMapping("/api/v1/admin/import")
    public LegacyImporter.ImportResult importLegacy(@RequestParam(defaultValue = "false") boolean replace) {
        if (!importApiEnabled) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "POST /admin/import is disabled (scheduling.import.api-enabled=false)", null);
        }
        return importer.importAll(replace);
    }

    private static RefEntity entity(String path) {
        return RefEntity.byPath(path).orElseThrow(() -> ApiException.badRequest("VALIDATION",
                "entity must be one of exams, test-centers, rooms, candidates"));
    }

    // ------------------------------------------------------------------ reconciler

    @GetMapping("/api/v1/reconciler/runs")
    public List<SeatReconciler.Run> runs(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
                                         @RequestParam(defaultValue = "20") int max) {
        return reconciler.runs(from, to, Math.max(1, Math.min(max, 500)));
    }

    /** sessionIds (contract addition) restricts the run; omitted = every OPEN session, as capacity-sync did. */
    @PostMapping("/api/v1/reconciler/runs")
    public ResponseEntity<?> runNow(@RequestParam(required = false) List<Integer> sessionIds,
                                    @RequestParam(defaultValue = "MANUAL") String trigger,
                                    @RequestHeader(value = "X-Actor", required = false) String actor) {
        try {
            return ResponseEntity.ok(reconciler.run(trigger, sessionIds, actor));
        } catch (SeatReconciler.AlreadyRunning e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("RECONCILER_RUNNING", e.getMessage(), null, null));
        }
    }

    // ------------------------------------------------------------------ audit (contract addition)

    @GetMapping("/api/v1/audit")
    public List<ReadModels.AuditEntry> audit(@RequestParam String entityName, @RequestParam int entityId) {
        return reads.audit(entityName, entityId);
    }
}
