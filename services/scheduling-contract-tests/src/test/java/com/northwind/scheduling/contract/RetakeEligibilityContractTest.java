package com.northwind.scheduling.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.northwind.scheduling.contract.support.Db;
import com.northwind.scheduling.contract.support.Fixtures;
import com.northwind.scheduling.contract.support.Fixtures.Cand;
import com.northwind.scheduling.contract.support.Fixtures.Ref;
import com.northwind.scheduling.contract.support.Http;
import com.northwind.scheduling.contract.support.LegacyEstate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * HB-07: the two retake-eligibility engines that read Result/Exam/ExamSession across the scheduling boundary.
 * Every case in retake-cases.psv is materialised as real Exam/ExamSession/Booking/Result rows, then evaluated by
 * (a) booking-api over HTTP (RetakeEligibilityService + ResultDao, working days), and
 * (b) the portal: attempts read with EligibilityService's query (PortalQueries.PRIOR_ATTEMPTS) and fed to the REAL
 *     AssessmentDelivery.Business RetakeRules.cs compiled and run with Mono, plus a Java transliteration of it
 *     (PortalRetakeRules) that runs everywhere and that Phase 2 can reuse.
 * Inventory: P-E01, BA-09..BA-11.
 */
@DisplayName("Retake eligibility: portal (calendar days) vs booking-api (working days)")
class RetakeEligibilityContractTest {

    record Case(String id, int waitDays, String attempts, LocalDate requested, boolean portalEligible, String portalReason,
                String portalEarliest, boolean partnerEligible, String partnerReason, String partnerEarliest) {
        @Override
        public String toString() {
            return id;
        }
    }

    record Materialised(Cand cand, Ref ref) {
    }

    static Db db;
    static Http api;
    static Http scheduling;
    static Fixtures fx;
    static final Map<String, Materialised> MATERIALISED = new HashMap<>();
    static Map<String, String[]> monoResults;

    static List<Case> cases() throws IOException {
        try (InputStream in = RetakeEligibilityContractTest.class.getResourceAsStream("/retake-cases.psv")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(l -> !l.isBlank() && !l.startsWith("#"))
                    .map(l -> l.split("\\|", -1))
                    .map(p -> new Case(p[0], Integer.parseInt(p[1]), p[2], LocalDate.parse(p[3]), Boolean.parseBoolean(p[4]),
                            p[5], nul(p[6]), Boolean.parseBoolean(p[7]), p[8], nul(p[9])))
                    .collect(Collectors.toList());
        }
    }

    static String nul(String s) {
        return "-".equals(s) ? null : s;
    }

    @BeforeAll
    static void up() throws Exception {
        LegacyEstate estate = LegacyEstate.get();
        db = estate.db;
        api = estate.bookingApi;
        scheduling = estate.scheduling;
        fx = new Fixtures(estate);
        for (Case c : cases()) {
            Ref ref = fx.reference(650, c.waitDays());
            Cand cand = fx.candidate();
            if (c.attempts() != null && !"-".equals(c.attempts())) {
                for (String a : c.attempts().split(";")) {
                    String[] parts = a.split("@");
                    LocalDateTime startsAt = LocalDateTime.parse(parts[0]);
                    boolean passed = "P".equals(parts[1]);
                    int s = fx.session(ref, startsAt, 10, 0, 9, "DELIVERED");
                    int b = fx.booking(s, cand, "ATTENDED", startsAt.minusDays(14));
                    fx.result(b, cand, ref, s, passed ? 800 : 500, passed, startsAt.plusDays(1));
                }
            }
            MATERIALISED.put(c.id(), new Materialised(cand, ref));
        }
        monoResults = LegacyEstate.SCHEDULING ? null : runRealPortalRules();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("booking-api GET /api/v1/candidates/{n}/eligibility (working days, latest attempt by StartsAt DESC)")
    void partner(Case c) {
        Materialised m = MATERIALISED.get(c.id());
        JsonNode body = api.get("/api/v1/candidates/" + m.cand().candidateNumber() + "/eligibility?examCode="
                + m.ref().examCode() + "&date=" + c.requested()).body();
        assertThat(body.get("eligible").asBoolean()).isEqualTo(c.partnerEligible());
        assertThat(body.get("reason").asText()).isEqualTo(c.partnerReason());
        JsonNode earliest = body.get("earliestRetakeDate");
        assertThat(earliest == null || earliest.isNull() ? null : earliest.asText()).isEqualTo(c.partnerEarliest());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("portal EligibilityService query + Java transliteration of RetakeRules (calendar days, Max(SessionDate))")
    void portalJavaTransliteration(Case c) {
        assumeTrue(!LegacyEstate.SCHEDULING, "portal reads Result/ExamSession itself only on the legacy target");
        PortalRetakeRules.Decision d = PortalRetakeRules.check(portalAttempts(c), c.waitDays(), c.requested());
        assertThat(d.eligible()).isEqualTo(c.portalEligible());
        assertThat(d.reason()).isEqualTo(c.portalReason());
        assertThat(d.earliest() == null ? null : d.earliest().toString()).isEqualTo(c.portalEarliest());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("portal EligibilityService query + REAL RetakeRules.cs under Mono")
    void portalRealCSharp(Case c) {
        assumeTrue(!LegacyEstate.SCHEDULING, "portal reads Result/ExamSession itself only on the legacy target");
        assumeTrue(monoResults != null, "mcs/mono not on PATH: real C# RetakeRules not executed (Java transliteration still ran)");
        String[] r = monoResults.get(c.id());
        assertThat(Boolean.parseBoolean(r[1])).isEqualTo(c.portalEligible());
        assertThat(r[2]).isEqualTo(c.portalReason());
        assertThat(nul(r[3])).isEqualTo(c.portalEarliest());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("portal EligibilityService -> scheduling-service GET /eligibility/retake?policy=PORTAL_CALENDAR_DAYS (ADR-001)")
    void portalViaSchedulingService(Case c) {
        assumeTrue(LegacyEstate.SCHEDULING, "scheduling target only");
        Materialised m = MATERIALISED.get(c.id());
        JsonNode body = scheduling.get("/api/v1/eligibility/retake?candidateNumber=" + m.cand().candidateNumber()
                + "&examCode=" + m.ref().examCode() + "&date=" + c.requested() + "&policy=PORTAL_CALENDAR_DAYS").body();
        assertThat(body.get("eligible").asBoolean()).isEqualTo(c.portalEligible());
        assertThat(body.get("reason").asText()).isEqualTo(c.portalReason());
        JsonNode earliest = body.get("earliestRetakeDate");
        assertThat(earliest == null || earliest.isNull() ? null : earliest.asText()).isEqualTo(c.portalEarliest());
    }

    static List<PortalRetakeRules.Attempt> portalAttempts(Case c) {
        Materialised m = MATERIALISED.get(c.id());
        return db.query(PortalQueries.PRIOR_ATTEMPTS, m.cand().candidateId(), m.ref().examId()).stream()
                .map(r -> new PortalRetakeRules.Attempt((LocalDateTime) r.get("SessionDate"), (Boolean) r.get("Passed")))
                .collect(Collectors.toList());
    }

    static Map<String, String[]> runRealPortalRules() throws Exception {
        if (!onPath("mcs") || !onPath("mono")) {
            return null;
        }
        Path root = LegacyEstate.repoRoot();
        Path rules = root.resolve("AssessmentDelivery.Business/Rules");
        Path out = root.resolve("services/scheduling-contract-tests/target/retake-rules-runner.exe");
        Files.createDirectories(out.getParent());
        List<String> compile = new ArrayList<>(List.of("mcs", "-nologo", "-target:exe", "-out:" + out));
        for (String f : List.of("RetakeRules.cs", "IRetakeRules.cs", "PriorAttempt.cs", "RetakeDecision.cs")) {
            compile.add(rules.resolve(f).toString());
        }
        compile.add(root.resolve("services/scheduling-contract-tests/src/test/dotnet/RetakeRulesRunner.cs").toString());
        run(compile, null);

        StringBuilder in = new StringBuilder();
        DateTimeFormatter iso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
        for (Case c : cases()) {
            List<PortalRetakeRules.Attempt> attempts = portalAttempts(c);
            String a = attempts.isEmpty() ? "-" : attempts.stream()
                    .map(x -> iso.format(x.sessionDate()) + "@" + (x.passed() ? "P" : "F")).collect(Collectors.joining(";"));
            in.append(c.id()).append('|').append(c.waitDays()).append('|').append(c.requested()).append('|').append(a).append('\n');
        }
        String stdout = run(List.of("mono", out.toString()), in.toString());
        Map<String, String[]> results = new HashMap<>();
        stdout.lines().filter(l -> !l.isBlank()).forEach(l -> results.put(l.split("\\|")[0], l.split("\\|", -1)));
        return results;
    }

    static boolean onPath(String exe) {
        return java.util.Arrays.stream(System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator))
                .anyMatch(d -> Files.isExecutable(Path.of(d, exe)));
    }

    static String run(List<String> cmd, String stdin) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        if (stdin != null) {
            p.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(120, TimeUnit.SECONDS) || p.exitValue() != 0) {
            throw new IllegalStateException(String.join(" ", cmd) + " failed:\n" + out);
        }
        return out;
    }

    /**
     * Line-for-line Java transliteration of AssessmentDelivery.Business/Rules/RetakeRules.cs (portal policy).
     * Calendar days from Max(SessionDate).Date; reason text formatted en-GB "dd MMM yyyy".
     */
    static final class PortalRetakeRules {
        record Attempt(LocalDateTime sessionDate, boolean passed) {
        }

        record Decision(boolean eligible, String reason, LocalDate earliest) {
        }

        static Decision check(List<Attempt> attempts, int retakeWaitDays, LocalDate requestedDate) {
            if (attempts.isEmpty()) {
                return new Decision(true, "No previous attempts", null);
            }
            if (attempts.stream().anyMatch(Attempt::passed)) {
                return new Decision(false, "Candidate has already passed this exam", null);
            }
            LocalDate last = attempts.stream().map(Attempt::sessionDate).max(Comparator.naturalOrder()).orElseThrow().toLocalDate();
            LocalDate earliest = last.plusDays(retakeWaitDays);
            if (requestedDate.isBefore(earliest)) {
                return new Decision(false, "Retake wait period applies until "
                        + earliest.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.UK)), earliest);
            }
            return new Decision(true, "Retake wait period has elapsed", earliest);
        }
    }
}
