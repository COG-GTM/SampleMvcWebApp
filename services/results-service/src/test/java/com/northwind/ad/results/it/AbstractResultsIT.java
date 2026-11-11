package com.northwind.ad.results.it;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/** Full app on a random port against the seeded SQL Server container. */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = SqlServerDb.Initializer.class)
public abstract class AbstractResultsIT {

    // NW00000901.. have no bookings in the seed; each fixture booking takes the next one
    private static final AtomicInteger NEXT_CANDIDATE = new AtomicInteger(901);

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected TestRestTemplate rest;

    /** sa: fixtures write dbo.Booking, which results-service's login is denied (040_single_writer.sql). */
    protected final JdbcTemplate sa = SqlServerDb.saJdbc();

    /** A fresh BOOKED booking with no result. */
    protected Fixture newBooking() {
        String candidateNumber = String.format("NW%08d", NEXT_CANDIDATE.getAndIncrement());
        int sessionId = sa.queryForObject(
                "SELECT TOP 1 SessionId FROM dbo.ExamSession WHERE Status = 'OPEN' AND SeatsAvailable > 0 "
              + "ORDER BY SessionId", Integer.class);
        int bookingId = sa.queryForObject(
                "SET NOCOUNT ON; INSERT INTO dbo.Booking (SessionId, CandidateId, Status, Channel) "
              + "SELECT ?, CandidateId, 'BOOKED', 'PARTNER' FROM dbo.Candidate WHERE CandidateNumber = ?; "
              + "SELECT CAST(SCOPE_IDENTITY() AS INT);", Integer.class, sessionId, candidateNumber);
        return new Fixture(bookingId, candidateNumber, sessionId);
    }

    protected int resultCount(int bookingId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Result WHERE BookingId = ?", Integer.class, bookingId);
    }

    protected Map<String, Object> result(int bookingId) {
        return jdbc.queryForMap("SELECT ResultId, Score, Passed, CONVERT(VARCHAR(19), ScoredAt, 126) AS ScoredAt, "
                + "SourceFile, ReceivedAt FROM dbo.Result WHERE BookingId = ?", bookingId);
    }

    protected Map<String, Object> booking(int bookingId) {
        return jdbc.queryForMap("SELECT Status, LastModified FROM dbo.Booking WHERE BookingId = ?", bookingId);
    }

    protected int auditCount(int resultId, String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditLog WHERE EntityName = 'Result' AND EntityId = ? "
                + "AND Action = ?", Integer.class, resultId, action);
    }

    protected int auditCount(int resultId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditLog WHERE EntityName = 'Result' AND EntityId = ?",
                Integer.class, resultId);
    }

    protected static final class Fixture {
        final int bookingId;
        final String candidateNumber;
        final int sessionId;

        Fixture(int bookingId, String candidateNumber, int sessionId) {
            this.bookingId = bookingId;
            this.candidateNumber = candidateNumber;
            this.sessionId = sessionId;
        }

        String csvRow(int score, String scoredAt) {
            return candidateNumber + "," + sessionId + "," + score + "," + scoredAt;
        }
    }
}
