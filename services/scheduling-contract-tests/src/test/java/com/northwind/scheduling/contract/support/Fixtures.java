package com.northwind.scheduling.contract.support;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixture builder. Writes reference and scheduling rows directly (the way db/seed does), so tests control every
 * value. Bookings are inserted with Channel='PORTAL' by default so trg_Booking_SeatSync (032) leaves
 * SeatsAvailable alone; callers set SeatsAvailable explicitly when it matters.
 * Scheduling target: reference rows still go to AssessmentDelivery (their system of record) and are replicated with
 * POST /api/v1/reference/sync; ExamSession/Booking/Waitlist rows go to the Scheduling database (same tables, no
 * triggers, so a PARTNER BOOKED fixture row does not take a second seat: HB-01 is gone by design); each Result also
 * gets its dbo.Attempt twin (what the attendance result block records), which retake eligibility reads.
 */
public final class Fixtures {

    private static final AtomicInteger SEQ = new AtomicInteger(100);

    private final Db db;
    private final Db sched;
    private final Http scheduling;

    public Fixtures(LegacyEstate estate) {
        this.db = estate.db;
        this.sched = estate.sched;
        this.scheduling = estate.scheduling;
    }

    private void replicateReference() {
        if (scheduling != null) {
            Http.Resp r = scheduling.postJson("/api/v1/reference/sync?full=true", Map.of());
            if (r.status() != 200) {
                throw new IllegalStateException("reference sync failed: " + r.status() + " " + r.body());
            }
        }
    }

    public static int next() {
        return SEQ.incrementAndGet();
    }

    public record Ref(int examId, String examCode, int centerId, String centerCode, int roomId) {
    }

    public Ref reference(int passingScore, int retakeWaitDays) {
        int n = next();
        String examCode = "CT-EX" + n;
        String centerCode = "CT" + n;
        int examId = db.insert("INSERT INTO dbo.Exam (ExamCode, Title, OwnerName, DurationMinutes, PassingScore, RetakeWaitDays) "
                + "VALUES (?, ?, 'Contract Owner', 90, ?, ?)", examCode, "Contract Exam " + n, passingScore, retakeWaitDays);
        int centerId = db.insert("INSERT INTO dbo.TestCenter (CenterCode, Name, City, Region, TimeZone) "
                + "VALUES (?, ?, 'Testville', 'EMEA', 'Europe/London')", centerCode, "Contract Centre " + n);
        int roomId = db.insert("INSERT INTO dbo.Room (TestCenterId, RoomName, SeatCount, AccommodationSeats) VALUES (?, ?, 20, 2)",
                centerId, "Room " + n);
        replicateReference();
        return new Ref(examId, examCode, centerId, centerCode, roomId);
    }

    public int session(Ref ref, LocalDateTime startsAt, int capacity, int accCap, int seatsAvailable, String status) {
        return sched.insert("INSERT INTO dbo.ExamSession (ExamId, TestCenterId, RoomId, StartsAt, Capacity, AccommodationCapacity, SeatsAvailable, Status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", ref.examId(), ref.centerId(), ref.roomId(), startsAt, capacity, accCap,
                seatsAvailable, status);
    }

    public record Cand(int candidateId, String candidateNumber) {
    }

    public Cand candidate() {
        return candidate(null);
    }

    public Cand candidate(String defaultAccommodation) {
        int n = next();
        String number = String.format("NW9%07d", n);
        int id = db.insert("INSERT INTO dbo.Candidate (CandidateNumber, FirstName, LastName, Email, DefaultAccommodationCode) "
                + "VALUES (?, 'Casey', ?, ?, ?)", number, "Contract" + n, "casey" + n + "@example.test", defaultAccommodation);
        replicateReference();
        return new Cand(id, number);
    }

    public int booking(int sessionId, Cand c, String status, LocalDateTime bookedAt) {
        return booking(sessionId, c, status, bookedAt, null, "PORTAL", null);
    }

    public int booking(int sessionId, Cand c, String status, LocalDateTime bookedAt, String accommodation, String channel,
                       String externalRef) {
        int id = sched.insert("INSERT INTO dbo.Booking (SessionId, CandidateId, Status, AccommodationCode, Channel, ExternalRef, BookedAt, LastModified) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", sessionId, c.candidateId(), status, accommodation, channel, externalRef,
                bookedAt, bookedAt);
        if ("CANCELLED".equals(status)) {
            sched.update("UPDATE dbo.Booking SET CancelledAt = ?, CancelReason = 'fixture' WHERE BookingId = ?", bookedAt.plusHours(1), id);
        }
        return id;
    }

    public void waitlist(int sessionId, Cand c, int bookingId, int position) {
        sched.insert("INSERT INTO dbo.Waitlist (SessionId, CandidateId, BookingId, Position) VALUES (?, ?, ?, ?)",
                sessionId, c.candidateId(), bookingId, position);
    }

    public int result(int bookingId, Cand c, Ref ref, int sessionId, int score, boolean passed, LocalDateTime scoredAt) {
        int id = db.insert("INSERT INTO dbo.Result (BookingId, CandidateId, ExamId, SessionId, Score, Passed, ScoredAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", bookingId, c.candidateId(), ref.examId(), sessionId, score, passed, scoredAt);
        if (sched != db) {
            sched.update("INSERT INTO dbo.Attempt (ResultId, BookingId, CandidateId, ExamId, SessionId, Score, Passed, ScoredAt) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, bookingId, c.candidateId(), ref.examId(), sessionId, score, passed, scoredAt);
        }
        return id;
    }
}
