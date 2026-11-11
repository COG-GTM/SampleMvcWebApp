package com.northwind.ad.results.functional;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.BeforeClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import com.northwind.ad.results.client.AttendanceClient;

/**
 * One SQL Server 2022 container per test JVM, built exactly like {@code db/apply.sh}
 * (schema, procs, triggers, deterministic seed, security). results-service connects as ad_results, like in
 * dev, so the single-writer DENYs on dbo.Booking apply. scheduling-service is replaced by a mock
 * {@link AttendanceClient}.
 * Tests claim their own seeded bookings so they do not depend on each other.
 */
public abstract class SqlServerTestSupport {

    static final String SA_PASSWORD = "Str0ng!Passw0rd";

    private static final Set<Integer> CLAIMED = Collections.synchronizedSet(new HashSet<Integer>());

    @SuppressWarnings("rawtypes")
    static final MSSQLServerContainer SQL;

    static {
        File dbDir;
        try {
            dbDir = new File("../../db").getCanonicalFile();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        SQL = newContainer(dbDir);
        SQL.start();
        try {
            for (String folder : Arrays.asList("schema", "procs", "triggers", "seed", "security")) {
                File[] files = new File(dbDir, folder).listFiles();
                List<String> names = new ArrayList<String>();
                for (File f : files) {
                    if (f.getName().endsWith(".sql")) {
                        names.add(f.getName());
                    }
                }
                Collections.sort(names);
                for (String name : names) {
                    Container.ExecResult r = SQL.execInContainer("/opt/mssql-tools18/bin/sqlcmd", "-S", "localhost",
                            "-U", "sa", "-P", SA_PASSWORD, "-C", "-b", "-I", "-d", "master",
                            "-i", "/legacy-db/" + folder + "/" + name);
                    if (r.getExitCode() != 0) {
                        throw new IllegalStateException(folder + "/" + name + " failed: " + r.getStdout() + r.getStderr());
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        System.setProperty("spring.datasource.url", "jdbc:sqlserver://" + SQL.getHost() + ":"
                + SQL.getMappedPort(1433) + ";databaseName=AssessmentDelivery;encrypt=true;trustServerCertificate=true");
        System.setProperty("spring.datasource.username", "ad_results");
        System.setProperty("spring.datasource.password", "Resu1ts!Dev2026");
    }

    @SuppressWarnings({"rawtypes", "unchecked", "resource"})
    private static MSSQLServerContainer newContainer(File dbDir) {
        return (MSSQLServerContainer) new MSSQLServerContainer(
                DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest"))
                .acceptLicense()
                .withPassword(SA_PASSWORD)
                .withCopyFileToContainer(MountableFile.forHostPath(dbDir.getPath()), "/legacy-db");
    }

    /** Forces the container + database to exist before Spring builds the context. */
    @BeforeClass
    public static void database() {
        SQL.isRunning();
    }

    @MockBean
    protected AttendanceClient attendance;

    @Autowired
    protected TestRestTemplate http;

    @Autowired
    protected JdbcTemplate jdbc;

    /** A seeded booking (BookingId, CandidateNumber, SessionId) that the score file lookup resolves to. */
    private static final String FROM_BOOKING =
            " FROM dbo.Booking b JOIN dbo.Candidate c ON c.CandidateId = b.CandidateId ";

    private static final String RESOLVABLE =
            "WHERE b.Status IN ('BOOKED','ATTENDED') AND NOT EXISTS (SELECT 1 FROM dbo.Booking b2 "
          + "  WHERE b2.CandidateId = b.CandidateId AND b2.SessionId = b.SessionId "
          + "  AND b2.Status IN ('BOOKED','ATTENDED') AND b2.BookingId > b.BookingId) ";

    /** BOOKED bookings with no result yet, not used by any other test. */
    protected List<Map<String, Object>> claimFreshBookings(int n) {
        return claim(jdbc.queryForList("SELECT TOP 500 b.BookingId, c.CandidateNumber, b.SessionId" + FROM_BOOKING + RESOLVABLE
                + "AND b.Status = 'BOOKED' AND NOT EXISTS (SELECT 1 FROM dbo.Result r WHERE r.BookingId = b.BookingId) "
                + "ORDER BY b.BookingId"), n);
    }

    protected Map<String, Object> claimFreshBooking() {
        return claimFreshBookings(1).get(0);
    }

    /** Seeded results (BookingId, CandidateNumber, SessionId, ResultId, Score, ScoredAt), not used by any other test. */
    protected List<Map<String, Object>> claimSeededResults(int n) {
        return claim(jdbc.queryForList("SELECT TOP 500 b.BookingId, c.CandidateNumber, b.SessionId, r.ResultId, r.Score, "
                + "CONVERT(VARCHAR(19), r.ScoredAt, 126) AS ScoredAt" + FROM_BOOKING
                + "JOIN dbo.Result r ON r.BookingId = b.BookingId " + RESOLVABLE
                + "AND b.Status = 'ATTENDED' ORDER BY b.BookingId DESC"), n);
    }

    private static List<Map<String, Object>> claim(List<Map<String, Object>> candidates, int n) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : candidates) {
            if (out.size() == n) {
                break;
            }
            if (CLAIMED.add(((Number) row.get("BookingId")).intValue())) {
                out.add(row);
            }
        }
        if (out.size() < n) {
            throw new IllegalStateException("not enough seeded bookings left");
        }
        return out;
    }

    protected static int id(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).intValue();
    }

    @SuppressWarnings("rawtypes")
    protected ResponseEntity<Map> postResult(int bookingId, int score, String scoredAt) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("bookingId", bookingId);
        body.put("score", score);
        if (scoredAt != null) {
            body.put("scoredAt", scoredAt);
        }
        return http.postForEntity("/api/v1/results", body, Map.class);
    }

    @SuppressWarnings("rawtypes")
    protected ResponseEntity<Map> upload(final String fileName, String csv) throws Exception {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", new ByteArrayResource(csv.getBytes("UTF-8")) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.postForEntity("/api/v1/results/upload",
                new HttpEntity<MultiValueMap<String, Object>>(parts, headers), Map.class);
    }

    protected Map<String, Object> resultRow(int bookingId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT ResultId, BookingId, Score, Passed, ScoredAt, SourceFile, ReceivedAt FROM dbo.Result WHERE BookingId = ?",
                bookingId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    protected int resultCount(int bookingId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Result WHERE BookingId = ?", Integer.class, bookingId);
    }

    protected int totalResults() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Result", Integer.class);
    }

    protected Map<String, Object> bookingRow(int bookingId) {
        return jdbc.queryForMap("SELECT Status, LastModified FROM dbo.Booking WHERE BookingId = ?", bookingId);
    }

    protected int auditCount(int resultId, String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditLog WHERE EntityName = 'Result' AND EntityId = ? AND Action = ?",
                Integer.class, resultId, action);
    }

    protected List<String> auditDetails(int resultId, String action) {
        return jdbc.queryForList("SELECT Detail FROM dbo.AuditLog WHERE EntityName = 'Result' AND EntityId = ? AND Action = ? "
                + "ORDER BY AuditId", String.class, resultId, action);
    }

    protected static String ts(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(d);
    }
}
