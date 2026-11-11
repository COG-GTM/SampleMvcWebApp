package com.northwind.scheduling.contract;

/**
 * SQL equivalents of the portal's EF6 LINQ reads of the scheduling tables. The .NET Framework 4.8 portal cannot run
 * on Linux, so these are hand translations of the LINQ in AssessmentDelivery.Services (same predicates, joins,
 * projected columns and ORDER BY; string equality is server-side, so collation rules match EF). Line numbers refer to
 * the inventory in docs/to-be/scheduling-boundary.md. If a LINQ query changes, change its twin here.
 */
final class PortalQueries {

    private PortalQueries() {
    }

    /** BookingService.Project (SessionListItem) columns; INNER joins because the FKs are NOT NULL. */
    static final String SESSION_PROJECTION = "SELECT s.SessionId, e.ExamCode, e.Title AS ExamTitle, tc.CenterCode, tc.Name AS CenterName, "
            + "tc.City, r.RoomName, s.StartsAt, e.DurationMinutes, s.Capacity, s.AccommodationCapacity, s.SeatsAvailable, s.Status "
            + "FROM dbo.ExamSession s JOIN dbo.Exam e ON e.ExamId = s.ExamId "
            + "JOIN dbo.TestCenter tc ON tc.TestCenterId = s.TestCenterId JOIN dbo.Room r ON r.RoomId = s.RoomId ";

    /** BookingService.FindOpenSessions (P-B01): OPEN only; From = date, To = date + 1 day exclusive. */
    static final String FIND_OPEN_SESSIONS = SESSION_PROJECTION
            + "WHERE s.Status = 'OPEN' AND (? IS NULL OR e.ExamCode = ?) AND (? IS NULL OR tc.CenterCode = ?) "
            + "AND (? IS NULL OR s.StartsAt >= ?) AND (? IS NULL OR s.StartsAt < DATEADD(day, 1, ?)) "
            + "ORDER BY s.StartsAt, tc.CenterCode";

    /** BookingService.GetSession (P-B02): no status filter. */
    static final String GET_SESSION = SESSION_PROJECTION + "WHERE s.SessionId = ?";

    /** BookingService.FindRescheduleTargets (P-B07): same exam, OPEN, other session, StartsAt >= DateTime.Today. */
    static final String RESCHEDULE_TARGETS = SESSION_PROJECTION
            + "WHERE s.ExamId = (SELECT s0.ExamId FROM dbo.Booking b0 JOIN dbo.ExamSession s0 ON s0.SessionId = b0.SessionId WHERE b0.BookingId = ?) "
            + "AND s.Status = 'OPEN' AND s.SessionId <> (SELECT SessionId FROM dbo.Booking WHERE BookingId = ?) AND s.StartsAt >= ? "
            + "ORDER BY s.StartsAt";

    /** BookingService.FindCandidate + GetBookingsForCandidate/ProjectBookings (P-B03/P-B04). */
    static final String CANDIDATE_BOOKINGS = "SELECT b.BookingId, b.SessionId, e.ExamCode, e.Title AS ExamTitle, tc.Name AS CenterName, "
            + "s.StartsAt, b.Status, b.AccommodationCode, b.Channel, b.ExternalRef, b.BookedAt, b.CancelledAt, b.CancelReason "
            + "FROM dbo.Booking b JOIN dbo.ExamSession s ON s.SessionId = b.SessionId JOIN dbo.Exam e ON e.ExamId = s.ExamId "
            + "JOIN dbo.TestCenter tc ON tc.TestCenterId = s.TestCenterId "
            + "WHERE b.CandidateId = (SELECT CandidateId FROM dbo.Candidate WHERE CandidateNumber = UPPER(LTRIM(RTRIM(?)))) "
            + "ORDER BY s.StartsAt DESC, b.BookingId DESC";

    /** AdminService.Load (P-A03): Booked is counted in memory with SeatCounter.CountBooked (BOOKED+ATTENDED). */
    static final String ADMIN_SESSION_ROWS = "SELECT s.SessionId, s.TestCenterId, tc.Name AS CenterName, e.ExamCode, e.Title, r.RoomName, "
            + "s.StartsAt, s.Capacity, s.AccommodationCapacity, s.SeatsAvailable, s.Status, "
            + "(SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId = s.SessionId AND b.Status IN ('BOOKED','ATTENDED')) AS Booked, "
            + "(SELECT COUNT(*) FROM dbo.Waitlist w WHERE w.SessionId = s.SessionId) AS WaitlistCount "
            + "FROM dbo.ExamSession s JOIN dbo.Exam e ON e.ExamId = s.ExamId JOIN dbo.TestCenter tc ON tc.TestCenterId = s.TestCenterId "
            + "JOIN dbo.Room r ON r.RoomId = s.RoomId WHERE s.TestCenterId = ? ORDER BY s.StartsAt";

    /**
     * AdminService.GetRoster (P-A07) + RosterCsv.Order: BOOKED/ATTENDED/WAITLISTED; position = MIN(Waitlist.Position)
     * for WAITLISTED rows; ordered seat holders first, then LastName, FirstName (case-insensitive), CandidateNumber.
     */
    static final String ROSTER = "SELECT c.CandidateNumber, c.LastName, c.FirstName, c.Email, b.Status AS BookingStatus, "
            + "b.AccommodationCode, b.Channel, b.BookedAt, "
            + "CASE WHEN b.Status = 'WAITLISTED' THEN (SELECT MIN(w.Position) FROM dbo.Waitlist w WHERE w.BookingId = b.BookingId) END AS WaitlistPosition "
            + "FROM dbo.Booking b JOIN dbo.Candidate c ON c.CandidateId = b.CandidateId "
            + "WHERE b.SessionId = ? AND b.Status IN ('BOOKED','ATTENDED','WAITLISTED') "
            + "ORDER BY CASE WHEN b.Status = 'WAITLISTED' THEN 1 ELSE 0 END, UPPER(c.LastName), UPPER(c.FirstName), c.CandidateNumber";

    /** EligibilityService.Check (P-E01): attempts for candidate+exam projected to PriorAttempt. */
    static final String PRIOR_ATTEMPTS = "SELECT s.StartsAt AS SessionDate, r.Passed, r.Score FROM dbo.Result r "
            + "JOIN dbo.ExamSession s ON s.SessionId = r.SessionId WHERE r.CandidateId = ? AND r.ExamId = ?";
}
