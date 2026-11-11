package com.northwind.scheduling;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.northwind.scheduling.legacy.LegacyReferenceSource;
import com.northwind.scheduling.legacy.LegacyRows;

/**
 * Testcontainers SQL Server 2022 for repository/API ITs. {@code disabledWithoutDocker = true}: on machines
 * without Docker (e.g. the Windows E2E VM) the ITs are reported as skipped, not failed.
 * The legacy source is a fixed in-memory fixture shaped like the AssessmentDelivery seed, so the ITs
 * never need the legacy DB.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class SqlServerTestBase {

    static final MSSQLServerContainer<?> SQL;

    static {
        MSSQLServerContainer<?> c = null;
        if (org.testcontainers.DockerClientFactory.instance().isDockerAvailable()) {
            c = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();
            c.start();
        }
        SQL = c;
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SQL.getJdbcUrl() + ";databaseName=Scheduling");
        r.add("spring.datasource.username", SQL::getUsername);
        r.add("spring.datasource.password", SQL::getPassword);
        r.add("scheduling.import.on-startup", () -> "true");
    }

    /** Seed-shaped fixture: centre SFO01, exam MHL-RN1 (sessions 154/163) and HPI-ACC2 (155), etc. */
    @TestConfiguration
    public static class FixtureLegacy {
        static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 1, 9, 0);

        @Bean
        @Primary
        LegacyReferenceSource fixtureLegacySource() {
            return new LegacyReferenceSource() {
                public List<LegacyRows.Center> centers() {
                    return List.of(new LegacyRows.Center(1, "SFO01", "Bayside Assessment Centre", "San Francisco", "NA-WEST",
                            "America/Los_Angeles", true, 1), new LegacyRows.Center(2, "AMS01", "Canal Ring Testing",
                            "Amsterdam", "EMEA", "Europe/Amsterdam", true, 1));
                }

                public List<LegacyRows.Room> rooms() {
                    return List.of(new LegacyRows.Room(1, 1, "Room North", 20, 2, true, 1),
                            new LegacyRows.Room(2, 2, "Room A", 10, 1, true, 1));
                }

                public List<LegacyRows.Exam> exams() {
                    return List.of(new LegacyRows.Exam(1, "MHL-RN1", "Registered Nurse Part 1", "MHL", 120, 700, 30, true, 1),
                            new LegacyRows.Exam(2, "HPI-ACC2", "Accounting 2", "HPI", 90, 650, 30, true, 1));
                }

                public List<LegacyRows.Candidate> candidates() {
                    return java.util.stream.IntStream.rangeClosed(1, 40).mapToObj(i -> new LegacyRows.Candidate(i,
                            String.format("NW%08d", 900 + i), "First" + i, "Last" + i, "c" + i + "@example.test",
                            i == 40 ? "ET25" : null, 1)).toList();
                }

                public List<LegacyRows.Result> results() {
                    // NW00000939 failed MHL-RN1 on 2026-11-07 (retake window), NW00000938 passed it
                    return List.of(new LegacyRows.Result(1, 39, 1, 900, LocalDateTime.of(2026, 11, 7, 9, 0), 500, false, T0, 1),
                            new LegacyRows.Result(2, 38, 1, 900, LocalDateTime.of(2026, 10, 1, 9, 0), 800, true, T0, 1));
                }

                public List<LegacyRows.Session> sessions() {
                    return List.of(
                            new LegacyRows.Session(154, 1, 1, 1, LocalDateTime.of(2026, 11, 12, 13, 30), 20, 2, "OPEN"),
                            new LegacyRows.Session(163, 1, 1, 1, LocalDateTime.of(2026, 11, 20, 9, 0), 3, 1, "OPEN"),
                            new LegacyRows.Session(155, 2, 1, 1, LocalDateTime.of(2026, 11, 13, 9, 0), 10, 0, "OPEN"),
                            new LegacyRows.Session(263, 1, 1, 1, LocalDateTime.of(2026, 11, 14, 9, 0), 10, 0, "CLOSED"),
                            new LegacyRows.Session(300, 1, 2, 2, LocalDateTime.of(2026, 12, 20, 9, 0), 10, 1, "OPEN"),
                            new LegacyRows.Session(900, 1, 1, 1, LocalDateTime.of(2026, 10, 1, 9, 0), 10, 0, "DELIVERED"));
                }

                public List<LegacyRows.Booking> bookings() {
                    // session 154: 9 booked (candidates 1-9); session 163: 3 booked -> full, one waitlisted
                    List<LegacyRows.Booking> b = new java.util.ArrayList<>();
                    for (int i = 1; i <= 9; i++) {
                        b.add(new LegacyRows.Booking(i, 154, i, "BOOKED", null, "PORTAL", null, T0.plusMinutes(i), null, null));
                    }
                    for (int i = 10; i <= 12; i++) {
                        b.add(new LegacyRows.Booking(i, 163, i, "BOOKED", i == 10 ? "ET25" : null, "PORTAL", null,
                                T0.plusMinutes(i), null, null));
                    }
                    b.add(new LegacyRows.Booking(13, 163, 13, "WAITLISTED", null, "PARTNER", "PX-1", T0.plusMinutes(13), null, null));
                    return b;
                }

                public List<LegacyRows.Waitlist> waitlist() {
                    return List.of(new LegacyRows.Waitlist(1, 163, 13, 13, 1, T0.plusMinutes(13)));
                }
            };
        }
    }
}
