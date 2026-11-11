package com.northwind.scheduling.characterization.legacy;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import com.northwind.scheduling.characterization.spi.Fixtures;
import com.northwind.scheduling.characterization.spi.Model.SessionSpec;

/**
 * Fixture rows in a dedicated test centre / room. Codes carry a per-run token so the suite can also run against an
 * existing database ({@code -Dlegacy.jdbcUrl}) repeatedly. Sessions start in 2031 by default, far from the seed's
 * 2026-11-10/11 anchors (HB-30), so nothing depends on the wall clock.
 */
final class LegacyFixtures implements Fixtures {

    private static final LocalDateTime BASE_START = LocalDateTime.of(2031, 1, 6, 9, 0);

    private final LegacyProcTarget t;
    private final String token = String.format("%05d", ThreadLocalRandom.current().nextInt(100_000));
    private final AtomicInteger seq = new AtomicInteger();
    private volatile int centerId;
    private volatile int roomId;

    LegacyFixtures(LegacyProcTarget t) {
        this.t = t;
    }

    private synchronized void ensureCentre() {
        if (centerId != 0) {
            return;
        }
        centerId = t.queryInt("SET NOCOUNT ON; INSERT INTO dbo.TestCenter (CenterCode, Name, City, Region, TimeZone) "
                + "VALUES (?, N'Characterization Centre', N'Nowhere', 'NA-EAST', 'America/New_York'); SELECT CAST(SCOPE_IDENTITY() AS INT);",
                "ZC" + token);
        roomId = t.queryInt("SET NOCOUNT ON; INSERT INTO dbo.Room (TestCenterId, RoomName, SeatCount, AccommodationSeats) "
                + "VALUES (?, N'Characterization Room', 500, 50); SELECT CAST(SCOPE_IDENTITY() AS INT);", centerId);
    }

    @Override
    public int newExam() {
        int n = seq.incrementAndGet();
        return t.queryInt("SET NOCOUNT ON; INSERT INTO dbo.Exam (ExamCode, Title, OwnerName, DurationMinutes, PassingScore) "
                + "VALUES (?, N'Characterization exam', N'Characterization', 120, 700); SELECT CAST(SCOPE_IDENTITY() AS INT);",
                "CHR" + token + "-" + n);
    }

    @Override
    public int newCandidate() {
        int n = seq.incrementAndGet();
        String number = "Z" + token + String.format("%06d", n);
        return t.queryInt("SET NOCOUNT ON; INSERT INTO dbo.Candidate (CandidateNumber, FirstName, LastName, Email) "
                + "VALUES (?, N'Char', N'Candidate', ?); SELECT CAST(SCOPE_IDENTITY() AS INT);",
                number, number.toLowerCase() + "@example.invalid");
    }

    @Override
    public int newSession(SessionSpec spec) {
        ensureCentre();
        int n = seq.incrementAndGet();
        LocalDateTime startsAt = spec.startsAt() != null ? spec.startsAt() : BASE_START.plusMinutes(n);
        int seats = spec.seatsAvailable() != null ? spec.seatsAvailable() : spec.capacity();
        return t.queryInt("SET NOCOUNT ON; INSERT INTO dbo.ExamSession (ExamId, TestCenterId, RoomId, StartsAt, Capacity, "
                + "AccommodationCapacity, SeatsAvailable, Status) VALUES (?, ?, ?, ?, ?, ?, ?, ?); SELECT CAST(SCOPE_IDENTITY() AS INT);",
                spec.examId(), centerId, roomId, startsAt, spec.capacity(), spec.accommodationCapacity(), seats,
                spec.status() == null ? "OPEN" : spec.status());
    }
}
