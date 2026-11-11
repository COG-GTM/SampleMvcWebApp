package com.northwind.scheduling.contract;

import com.northwind.scheduling.contract.support.Db;
import com.northwind.scheduling.contract.support.Fixtures;
import com.northwind.scheduling.contract.support.Fixtures.Cand;
import com.northwind.scheduling.contract.support.Fixtures.Ref;
import com.northwind.scheduling.contract.support.LegacyEstate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Pins the portal's EF6 reads of ExamSession/Booking/Waitlist at SQL level (see {@link PortalQueries}) plus the
 * proc error surface the portal maps to UI messages (SqlErrorHelper: number 50000, class 16). LIMITATION: the .NET
 * Framework 4.8 portal does not run on Linux, so this pins the query semantics and data shape the portal consumes,
 * not the C# code path itself; docs/to-be/portal-contract-spec.md is the matching spec for a Windows run.
 * Inventory: P-B01..P-B07, P-A03, P-A07.
 */
@DisplayName("portal (EF6, SQL-level) -> ExamSession/Booking/Waitlist reads and proc error surface")
class PortalReadContractTest {

    static Db db;
    static Fixtures fx;
    static Ref ref;
    static int openFuture;
    static int openStarted;
    static int closed;
    static int openFull;
    static Cand cand;
    static int bookingOnFuture;

    @BeforeAll
    static void up() {
        // Portal EF reads of AssessmentDelivery are legacy-only: Session 4 replaces them with scheduling-service calls,
        // whose parity is checked by the portal contract spec on Windows (docs/to-be/portal-contract-spec.md).
        assumeFalse(LegacyEstate.SCHEDULING, "portal EF reads only exist on the legacy target");
        db = LegacyEstate.get().db;
        fx = new Fixtures(LegacyEstate.get());
        ref = fx.reference(650, 30);
        openStarted = fx.session(ref, LocalDateTime.of(2026, 1, 10, 9, 0), 8, 0, 8, "OPEN");
        openFuture = fx.session(ref, LocalDateTime.of(2027, 5, 3, 13, 15), 6, 1, 4, "OPEN");
        closed = fx.session(ref, LocalDateTime.of(2027, 5, 4, 9, 0), 6, 0, 6, "CLOSED");
        openFull = fx.session(ref, LocalDateTime.of(2027, 5, 5, 9, 0), 1, 0, 0, "OPEN");
        cand = fx.candidate();
        bookingOnFuture = fx.booking(openFuture, cand, "BOOKED", LocalDateTime.of(2026, 9, 1, 8, 0));
        fx.booking(openStarted, cand, "CANCELLED", LocalDateTime.of(2025, 12, 1, 8, 0));
    }

    @Test
    @DisplayName("FindOpenSessions: OPEN only, includes already-started sessions (HB-31), SessionListItem columns, StartsAt order")
    void findOpenSessions() {
        List<Map<String, Object>> rows = db.query(PortalQueries.FIND_OPEN_SESSIONS, ref.examCode(), ref.examCode(), null, null,
                null, null, null, null);
        assertThat(rows).extracting(r -> r.get("SessionId")).containsExactly(openStarted, openFuture, openFull);
        Map<String, Object> f = rows.get(1);
        assertThat(f).containsOnlyKeys("SessionId", "ExamCode", "ExamTitle", "CenterCode", "CenterName", "City", "RoomName",
                "StartsAt", "DurationMinutes", "Capacity", "AccommodationCapacity", "SeatsAvailable", "Status");
        assertThat(f).containsEntry("ExamCode", ref.examCode()).containsEntry("CenterCode", ref.centerCode())
                .containsEntry("City", "Testville").containsEntry("StartsAt", LocalDateTime.of(2027, 5, 3, 13, 15))
                .containsEntry("DurationMinutes", 90).containsEntry("Capacity", 6).containsEntry("AccommodationCapacity", 1)
                .containsEntry("SeatsAvailable", 4).containsEntry("Status", "OPEN");
    }

    @Test
    @DisplayName("FindOpenSessions date window: From inclusive, To whole-day inclusive; exam code match is case-insensitive")
    void findOpenSessionsWindow() {
        LocalDate d = LocalDate.of(2027, 5, 3);
        List<Map<String, Object>> rows = db.query(PortalQueries.FIND_OPEN_SESSIONS, ref.examCode().toLowerCase(),
                ref.examCode().toLowerCase(), null, null, d, d, d, d);
        assertThat(rows).extracting(r -> r.get("SessionId")).containsExactly(openFuture);
    }

    @Test
    @DisplayName("GetSession has no status filter (CLOSED session still readable)")
    void getSession() {
        assertThat(db.one(PortalQueries.GET_SESSION, closed)).containsEntry("Status", "CLOSED");
    }

    @Test
    @DisplayName("FindRescheduleTargets: same exam, OPEN, not current session, StartsAt >= today")
    void rescheduleTargets() {
        List<Map<String, Object>> rows = db.query(PortalQueries.RESCHEDULE_TARGETS, bookingOnFuture, bookingOnFuture,
                LocalDate.of(2026, 11, 11));
        assertThat(rows).extracting(r -> r.get("SessionId")).containsExactly(openFull);
    }

    @Test
    @DisplayName("GetBookingsForCandidate: trimmed/upper-cased number, all statuses, StartsAt DESC, BookingId DESC")
    void candidateBookings() {
        List<Map<String, Object>> rows = db.query(PortalQueries.CANDIDATE_BOOKINGS, "  " + cand.candidateNumber().toLowerCase() + " ");
        assertThat(rows).extracting(r -> r.get("Status")).containsExactly("BOOKED", "CANCELLED");
        assertThat(rows.get(0)).containsEntry("BookingId", bookingOnFuture).containsEntry("Channel", "PORTAL")
                .containsEntry("ExternalRef", null).containsEntry("StartsAt", LocalDateTime.of(2027, 5, 3, 13, 15));
    }

    @Test
    @DisplayName("Admin session rows: Booked = BOOKED+ATTENDED rows, WaitlistCount = Waitlist rows (not WAITLISTED bookings)")
    void adminSessionRows() {
        Ref own = fx.reference(650, 30);
        int s = fx.session(own, LocalDateTime.of(2027, 6, 1, 9, 0), 2, 0, 0, "OPEN");
        Cand a = fx.candidate();
        Cand b = fx.candidate();
        Cand c = fx.candidate();
        Cand d = fx.candidate();
        fx.booking(s, a, "BOOKED", LocalDateTime.of(2026, 9, 1, 8, 0));
        fx.booking(s, b, "ATTENDED", LocalDateTime.of(2026, 9, 1, 8, 0));
        int w1 = fx.booking(s, c, "WAITLISTED", LocalDateTime.of(2026, 9, 2, 8, 0));
        fx.waitlist(s, c, w1, 1);
        fx.booking(s, d, "WAITLISTED", LocalDateTime.of(2026, 9, 3, 8, 0));
        assertThat(db.one(PortalQueries.ADMIN_SESSION_ROWS, own.centerId())).containsEntry("Booked", 2).containsEntry("WaitlistCount", 1);
    }

    @Test
    @DisplayName("Roster export: seat holders then waitlist, by name; WAITLISTED position from Waitlist (null if row missing)")
    void roster() {
        Ref own = fx.reference(650, 30);
        int s = fx.session(own, LocalDateTime.of(2027, 6, 2, 9, 0), 2, 0, 0, "OPEN");
        Cand zed = fx.candidate();
        Cand amy = fx.candidate();
        Cand wl = fx.candidate();
        Cand orphan = fx.candidate();
        Cand gone = fx.candidate();
        db.update("UPDATE dbo.Candidate SET LastName = 'Zed' WHERE CandidateId = ?", zed.candidateId());
        db.update("UPDATE dbo.Candidate SET LastName = 'amy' WHERE CandidateId = ?", amy.candidateId());
        db.update("UPDATE dbo.Candidate SET LastName = 'Bee' WHERE CandidateId IN (?, ?)", wl.candidateId(), orphan.candidateId());
        fx.booking(s, zed, "BOOKED", LocalDateTime.of(2026, 9, 1, 8, 0));
        fx.booking(s, amy, "ATTENDED", LocalDateTime.of(2026, 9, 1, 9, 0), "ET25", "PARTNER", "PX-9");
        int wb = fx.booking(s, wl, "WAITLISTED", LocalDateTime.of(2026, 9, 2, 8, 0));
        fx.waitlist(s, wl, wb, 3);
        fx.booking(s, orphan, "WAITLISTED", LocalDateTime.of(2026, 9, 3, 8, 0));
        fx.booking(s, gone, "CANCELLED", LocalDateTime.of(2026, 9, 3, 8, 0));

        List<Map<String, Object>> rows = db.query(PortalQueries.ROSTER, s);

        assertThat(rows).extracting(r -> r.get("CandidateNumber"))
                .containsExactly(amy.candidateNumber(), zed.candidateNumber(), wl.candidateNumber(), orphan.candidateNumber());
        assertThat(rows.get(0)).containsEntry("BookingStatus", "ATTENDED").containsEntry("Channel", "PARTNER")
                .containsEntry("AccommodationCode", "ET25").containsEntry("WaitlistPosition", null);
        assertThat(rows.get(2)).containsEntry("WaitlistPosition", 3);
        assertThat(rows.get(3)).containsEntry("WaitlistPosition", null);
    }

    record ProcError(int number, int state, String message) {
    }

    static ProcError call(String sql, Object... args) {
        try (Connection c = db.connect(); CallableStatement cs = c.prepareCall(sql)) {
            for (int i = 0; i < args.length; i++) {
                cs.setObject(i + 1, args[i]);
            }
            if (sql.contains("usp_BookSeat") || sql.contains("usp_RescheduleBooking")) {
                cs.registerOutParameter(args.length + 1, Types.INTEGER);
                cs.registerOutParameter(args.length + 2, Types.VARCHAR);
            }
            cs.execute();
            return null;
        } catch (SQLException e) {
            return new ProcError(e.getErrorCode(), e.getSQLState() == null ? -1 : stateOf(e), e.getMessage());
        }
    }

    static int stateOf(SQLException e) {
        if (e instanceof com.microsoft.sqlserver.jdbc.SQLServerException s && s.getSQLServerError() != null) {
            return s.getSQLServerError().getErrorState();
        }
        return -1;
    }

    @Test
    @DisplayName("HB-14: every proc RAISERROR surfaces as error 50000 / severity 16 with a distinct state 1..6 and fixed text")
    void procErrorSurface() {
        Cand c = fx.candidate();
        Ref other = fx.reference(650, 30);
        int otherSession = fx.session(other, LocalDateTime.of(2027, 7, 1, 9, 0), 5, 0, 5, "OPEN");
        int noAcc = fx.session(fx.reference(650, 30), LocalDateTime.of(2027, 7, 2, 9, 0), 5, 0, 5, "OPEN");
        int cancelled = fx.booking(openFuture, fx.candidate(), "CANCELLED", LocalDateTime.of(2026, 9, 1, 8, 0));
        String book = "{call dbo.usp_BookSeat(?, ?, ?, ?, ?)}";

        assertThat(call(book, closed, c.candidateId(), null))
                .isEqualTo(new ProcError(50000, 1, "Session " + closed + " is not open for booking."));
        assertThat(call(book, openFuture, cand.candidateId(), null))
                .isEqualTo(new ProcError(50000, 2, "Candidate " + cand.candidateId() + " already has an active booking on session " + openFuture + "."));
        assertThat(call(book, noAcc, c.candidateId(), "ET50"))
                .isEqualTo(new ProcError(50000, 3, "No accommodation seat available on session " + noAcc + "."));
        assertThat(call("{call dbo.usp_CancelBooking(?, ?, ?)}", cancelled, "x", "portal"))
                .isEqualTo(new ProcError(50000, 4, "Booking " + cancelled + " cannot be cancelled."));
        assertThat(call("{call dbo.usp_ChangeCapacity(?, ?, ?)}", openFuture, 0, "portal-admin"))
                .isEqualTo(new ProcError(50000, 5, "Invalid capacity 0 for session " + openFuture + "."));
        assertThat(call("{call dbo.usp_RescheduleBooking(?, ?, ?, ?)}", bookingOnFuture, otherSession))
                .isEqualTo(new ProcError(50000, 6, "Booking " + bookingOnFuture + " cannot be moved to session " + otherSession + "."));
    }
}
