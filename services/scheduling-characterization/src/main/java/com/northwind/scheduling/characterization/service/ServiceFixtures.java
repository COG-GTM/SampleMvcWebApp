package com.northwind.scheduling.characterization.service;

import com.northwind.scheduling.characterization.spi.Fixtures;
import com.northwind.scheduling.characterization.spi.Model.SessionSpec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Same fixture shapes as LegacyFixtures. Reference rows are written to AssessmentDelivery (their system of record) and
 * replicated by the service; sessions are created in the service's own database through POST /sessions.
 */
final class ServiceFixtures implements Fixtures {

    private static final LocalDateTime BASE_START = LocalDateTime.of(2031, 1, 6, 9, 0);

    private final SchedulingServiceTarget t;
    private final String token = String.format("%05d", ThreadLocalRandom.current().nextInt(100_000));
    private final AtomicInteger seq = new AtomicInteger();
    private volatile int centerId;
    private volatile int roomId;

    ServiceFixtures(SchedulingServiceTarget t) {
        this.t = t;
    }

    private int insert(String sql, Object... args) {
        try (Connection c = t.connectLegacy(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int id = rs.getInt(1);
                t.referenceChanged();
                return id;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private synchronized void ensureCentre() {
        if (centerId != 0) {
            return;
        }
        centerId = insert("SET NOCOUNT ON; INSERT INTO dbo.TestCenter (CenterCode, Name, City, Region, TimeZone) "
                + "VALUES (?, N'Characterization Centre', N'Nowhere', 'NA-EAST', 'America/New_York'); SELECT CAST(SCOPE_IDENTITY() AS INT);",
                "ZC" + token);
        roomId = insert("SET NOCOUNT ON; INSERT INTO dbo.Room (TestCenterId, RoomName, SeatCount, AccommodationSeats) "
                + "VALUES (?, N'Characterization Room', 500, 50); SELECT CAST(SCOPE_IDENTITY() AS INT);", centerId);
    }

    @Override
    public int newExam() {
        int n = seq.incrementAndGet();
        return insert("SET NOCOUNT ON; INSERT INTO dbo.Exam (ExamCode, Title, OwnerName, DurationMinutes, PassingScore) "
                + "VALUES (?, N'Characterization exam', N'Characterization', 120, 700); SELECT CAST(SCOPE_IDENTITY() AS INT);",
                "CHR" + token + "-" + n);
    }

    @Override
    public int newCandidate() {
        int n = seq.incrementAndGet();
        String number = "Z" + token + String.format("%06d", n);
        return insert("SET NOCOUNT ON; INSERT INTO dbo.Candidate (CandidateNumber, FirstName, LastName, Email) "
                + "VALUES (?, N'Char', N'Candidate', ?); SELECT CAST(SCOPE_IDENTITY() AS INT);", number,
                number.toLowerCase() + "@example.invalid");
    }

    @Override
    public int newSession(SessionSpec spec) {
        ensureCentre();
        int n = seq.incrementAndGet();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("examId", spec.examId());
        body.put("testCenterId", centerId);
        body.put("roomId", roomId);
        body.put("startsAt", spec.startsAt() != null ? spec.startsAt() : BASE_START.plusMinutes(n));
        body.put("capacity", spec.capacity());
        body.put("accommodationCapacity", spec.accommodationCapacity());
        body.put("seatsAvailable", spec.seatsAvailable() != null ? spec.seatsAvailable() : spec.capacity());
        body.put("status", spec.status() == null ? "OPEN" : spec.status());
        return t.http.require(t.http.send("POST", "/sessions", body, null), "create session").body().get("sessionId").asInt();
    }
}
