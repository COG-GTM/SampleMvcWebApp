package com.northwind.scheduling.characterization.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.northwind.scheduling.characterization.legacy.LegacyDatabase;
import com.northwind.scheduling.characterization.spi.Capability;
import com.northwind.scheduling.characterization.spi.ErrorCondition;
import com.northwind.scheduling.characterization.spi.Fixtures;
import com.northwind.scheduling.characterization.spi.Model.AuditEntry;
import com.northwind.scheduling.characterization.spi.Model.BookingState;
import com.northwind.scheduling.characterization.spi.Model.ExamSessionState;
import com.northwind.scheduling.characterization.spi.Model.WaitlistEntry;
import com.northwind.scheduling.characterization.spi.Outcome;
import com.northwind.scheduling.characterization.spi.RawStorageAccess;
import com.northwind.scheduling.characterization.spi.SchedulingError;
import com.northwind.scheduling.characterization.spi.SchedulingTarget;
import com.northwind.scheduling.characterization.spi.SeatDriftCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Drives services/scheduling-service over HTTP. By default it provisions SQL Server 2022 with db/apply.sh (exactly as
 * the legacy target does), runs services/scheduling-service/db/bootstrap.sql as sa, and launches the service jar
 * against it (Flyway creates the Scheduling schema, the initial import copies AssessmentDelivery). Fixtures write
 * reference rows (Exam, Candidate, TestCenter, Room) into AssessmentDelivery, which the service picks up through its
 * Change Tracking relay; sessions are created through POST /sessions. Raw-storage scenarios use the service's
 * direct-write compatibility API (/admin/storage). Seat drift runs services/scheduling-service/db/checks/seat-drift.sql
 * against the Scheduling database.
 *
 * External service: -Dscheduling.baseUrl=http://host:8084/api/v1 plus -Dlegacy.jdbcUrl (AssessmentDelivery) and
 * optionally -Dscheduling.jdbcUrl (defaults to the legacy URL with databaseName=Scheduling).
 */
public final class SchedulingServiceTarget implements SchedulingTarget {

    private static final Logger LOG = LoggerFactory.getLogger(SchedulingServiceTarget.class);

    final Http http;
    private final LegacyDatabase db;
    private final Process process;
    private final String adUrl;
    private final String schedulingUrl;
    private final String user;
    private final String password;
    private final Set<Capability> capabilities;
    private final ServiceFixtures fixtures;
    private final ServiceRawStorage raw;
    private final SeatDriftCheck drift;
    private final AtomicBoolean referenceDirty = new AtomicBoolean();

    private SchedulingServiceTarget(Http http, LegacyDatabase db, Process process, String adUrl, String schedulingUrl,
                                    String user, String password, Path repoRoot, boolean fresh) {
        this.http = http;
        this.db = db;
        this.process = process;
        this.adUrl = adUrl;
        this.schedulingUrl = schedulingUrl;
        this.user = user;
        this.password = password;
        this.capabilities = EnumSet.of(Capability.RAW_STORAGE, Capability.LEGACY_SQL_SEMANTICS);
        if (fresh) {
            capabilities.add(Capability.FRESH_DATABASE);
        }
        this.fixtures = new ServiceFixtures(this);
        this.raw = new ServiceRawStorage(this);
        this.drift = new ServiceSeatDriftCheck(this, repoRoot.resolve("services/scheduling-service/db/checks/seat-drift.sql"));
    }

    static SchedulingServiceTarget start() {
        Path root = LegacyDatabase.repoRoot();
        String external = System.getProperty("scheduling.baseUrl");
        if (external != null && !external.isBlank()) {
            String ad = System.getProperty("legacy.jdbcUrl");
            if (ad == null || ad.isBlank()) {
                throw new IllegalStateException("-Dscheduling.baseUrl needs -Dlegacy.jdbcUrl for fixtures");
            }
            String sched = System.getProperty("scheduling.jdbcUrl", ad.replaceFirst("databaseName=[^;]+", "databaseName=Scheduling"));
            LOG.info("Using running scheduling-service {}", external);
            return new SchedulingServiceTarget(new Http(external), null, null, ad, sched,
                    System.getProperty("legacy.user", "ad_app"), System.getProperty("legacy.password", "Northw1nd!Dev2011"), root, false);
        }
        Path jar = Path.of(System.getProperty("scheduling.jar", root.resolve("services/scheduling-service/target/scheduling-service.jar").toString()));
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException(jar + " not found: build it first with "
                    + "`mvn -B -f services/scheduling-service/pom.xml -DskipTests package`");
        }
        LegacyDatabase db = LegacyDatabase.start();
        Process p = null;
        try {
            if (db.provisionedByRun()) {
                db.runScriptAsSa(root.resolve("services/scheduling-service/db/bootstrap.sql"));
            }
            String sched = db.jdbcUrlFor("Scheduling");
            int port = freePort();
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            File log = root.resolve("services/scheduling-characterization/target/scheduling-service.log").toFile();
            log.getParentFile().mkdirs();
            p = new ProcessBuilder(java, "-jar", jar.toString(),
                    "--server.port=" + port,
                    "--spring.datasource.url=" + sched,
                    "--spring.datasource.username=" + db.user(),
                    "--spring.datasource.password=" + db.password(),
                    "--spring.datasource.hikari.maximum-pool-size=40",
                    "--scheduling.source.url=" + db.jdbcUrl(),
                    "--scheduling.source.username=" + db.user(),
                    "--scheduling.source.password=" + db.password(),
                    "--scheduling.import.on-startup=if-empty",
                    "--scheduling.reference.poll-ms=0",
                    "--scheduling.reconciler.cron=-",
                    "--scheduling.storage-api.enabled=true")
                    .redirectErrorStream(true).redirectOutput(log).start();
            Http http = new Http("http://localhost:" + port + "/api/v1");
            waitHealthy(p, "http://localhost:" + port, log);
            LOG.info("scheduling-service up on port {} (log {})", port, log);
            return new SchedulingServiceTarget(http, db, p, db.jdbcUrl(), sched, db.user(), db.password(), root, db.provisionedByRun());
        } catch (RuntimeException | IOException e) {
            if (p != null) {
                p.destroyForcibly();
            }
            db.close();
            throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
        }
    }

    private static void waitHealthy(Process p, String root, File log) {
        Http h = new Http(root);
        long deadline = System.currentTimeMillis() + 240_000;
        while (System.currentTimeMillis() < deadline) {
            if (!p.isAlive()) {
                throw new IllegalStateException("scheduling-service exited with " + p.exitValue() + ", see " + log);
            }
            try {
                Http.Response r = h.get("/actuator/health");
                if (r.ok()) {
                    return;
                }
            } catch (IllegalStateException notYet) {
                // not listening yet
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("scheduling-service did not become healthy, see " + log);
    }

    private static int freePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ plumbing

    Connection connectLegacy() throws SQLException {
        return DriverManager.getConnection(adUrl, user, password);
    }

    Connection connectScheduling() throws SQLException {
        return DriverManager.getConnection(schedulingUrl, user, password);
    }

    void referenceChanged() {
        referenceDirty.set(true);
    }

    /** Pushes fixture reference rows through the relay before a command needs them. */
    void syncReference() {
        if (referenceDirty.compareAndSet(true, false)) {
            http.require(http.send("POST", "/reference/sync", null, null), "reference sync");
        }
    }

    static SchedulingError toError(Http.Response r) {
        JsonNode b = r.body();
        String message = b == null || b.get("message") == null ? "HTTP " + r.status() : b.get("message").asText();
        Integer state = b != null && b.hasNonNull("legacyState") ? b.get("legacyState").asInt() : null;
        Integer sql = b != null && b.hasNonNull("sqlError") ? b.get("sqlError").asInt() : null;
        ErrorCondition condition = state != null ? ErrorCondition.fromLegacy(state, message) : ErrorCondition.SYSTEM_ERROR;
        return new SchedulingError(condition, condition.legacyCode(), message, state != null ? Integer.valueOf(50000) : sql, state);
    }

    private static Outcome outcome(Http.Response r) {
        if (!r.ok()) {
            return Outcome.failed(toError(r));
        }
        if (r.body() != null && r.body().has("bookingId") && r.body().has("status") && r.status() == 201) {
            return Outcome.booked(r.body().get("bookingId").asInt(), r.body().get("status").asText());
        }
        return Outcome.ok();
    }

    static LocalDateTime ldt(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : LocalDateTime.parse(v.asText());
    }

    static String str(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    // ------------------------------------------------------------------ SchedulingTarget

    @Override
    public String name() {
        return "scheduling-service";
    }

    @Override
    public Set<Capability> capabilities() {
        return capabilities;
    }

    @Override
    public Outcome book(int sessionId, int candidateId, String accommodationCode) {
        syncReference();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", sessionId);
        body.put("candidateId", candidateId);
        body.put("accommodationCode", accommodationCode);
        body.put("channel", "PORTAL");
        return outcome(http.send("POST", "/bookings", body, null));
    }

    @Override
    public Outcome cancel(int bookingId, String reason, String actor) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason);
        return outcome(http.send("POST", "/bookings/" + bookingId + "/cancel", body, actor));
    }

    @Override
    public Outcome changeCapacity(int sessionId, int newCapacity, String actor) {
        return outcome(http.send("POST", "/sessions/" + sessionId + "/capacity", Map.of("newCapacity", newCapacity), actor));
    }

    @Override
    public Outcome reschedule(int bookingId, int newSessionId) {
        syncReference();
        return outcome(http.send("POST", "/bookings/" + bookingId + "/reschedule",
                Map.of("newSessionId", newSessionId, "channel", "PORTAL"), null));
    }

    @Override
    public void markAttended(int bookingId) {
        Http.Response r = http.send("POST", "/bookings/" + bookingId + "/attendance", Map.of(), null);
        if (!r.ok() && r.status() != 409 && r.status() != 404) {
            throw new IllegalStateException("attendance -> HTTP " + r.status() + " " + r.body());
        }
    }

    @Override
    public int reconcileSeats(Collection<Integer> sessionIds) {
        if (sessionIds.isEmpty()) {
            return 0;
        }
        String ids = sessionIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        while (true) {
            Http.Response r = http.send("POST", "/reconciler/runs?trigger=characterization&sessionIds=" + ids, null, null);
            if (r.status() == 409) {
                Thread.onSpinWait();
                continue;
            }
            return http.require(r, "reconciler run").body().get("updated").asInt();
        }
    }

    @Override
    public ExamSessionState session(int sessionId) {
        Http.Response r = http.get("/sessions/" + sessionId);
        if (r.status() == 404) {
            return null;
        }
        JsonNode s = http.require(r, "session " + sessionId).body();
        return new ExamSessionState(s.get("sessionId").asInt(), s.get("examId").asInt(), s.get("capacity").asInt(),
                s.get("accommodationCapacity").asInt(), s.get("seatsAvailable").asInt(), str(s, "status"), ldt(s, "lastModified"));
    }

    private static BookingState booking(JsonNode b) {
        return new BookingState(b.get("bookingId").asInt(), b.get("sessionId").asInt(), b.get("candidateId").asInt(),
                str(b, "status"), str(b, "accommodationCode"), str(b, "channel"), str(b, "externalRef"), ldt(b, "bookedAt"),
                ldt(b, "cancelledAt"), str(b, "cancelReason"));
    }

    @Override
    public BookingState booking(int bookingId) {
        Http.Response r = http.get("/bookings/" + bookingId);
        if (r.status() == 404) {
            return null;
        }
        return booking(http.require(r, "booking " + bookingId).body());
    }

    @Override
    public List<BookingState> bookings(int sessionId) {
        List<BookingState> out = new ArrayList<>();
        for (JsonNode b : http.require(http.get("/bookings?sessionId=" + sessionId + "&sort=bookingId&limit=10000"), "bookings").body()) {
            out.add(booking(b));
        }
        out.sort(Comparator.comparingInt(BookingState::bookingId));
        return out;
    }

    @Override
    public List<WaitlistEntry> waitlist(int sessionId) {
        Http.Response r = http.get("/sessions/" + sessionId + "/waitlist");
        List<WaitlistEntry> out = new ArrayList<>();
        if (r.status() == 404) {
            return out;
        }
        for (JsonNode w : http.require(r, "waitlist").body()) {
            out.add(new WaitlistEntry(w.get("sessionId").asInt(), w.get("candidateId").asInt(), w.get("bookingId").asInt(),
                    w.get("position").asInt()));
        }
        return out;
    }

    @Override
    public List<AuditEntry> audit(String entityName, int entityId) {
        List<AuditEntry> out = new ArrayList<>();
        for (JsonNode a : http.require(http.get("/audit?entityName=" + entityName + "&entityId=" + entityId), "audit").body()) {
            out.add(new AuditEntry(str(a, "entityName"), a.get("entityId").asInt(), str(a, "action"), str(a, "detail"), str(a, "actor")));
        }
        return out;
    }

    @Override
    public Fixtures fixtures() {
        return fixtures;
    }

    @Override
    public SeatDriftCheck seatDriftCheck() {
        return drift;
    }

    @Override
    public Optional<RawStorageAccess> rawStorage() {
        return Optional.of(raw);
    }

    @Override
    public void close() {
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        if (db != null) {
            db.close();
        }
    }
}
