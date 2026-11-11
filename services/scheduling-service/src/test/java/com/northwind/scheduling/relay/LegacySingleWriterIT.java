package com.northwind.scheduling.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * db/security/036 + 040 (MBA-3823): every legacy login is refused booking/seat writes with a SQL Server permission
 * error, ad_sched_relay keeps them; 032/033 skip their side effects only for SESSION_CONTEXT writer =
 * scheduling-relay (HB-01, HB-04, HB-05 stay as they were for any other writer).
 */
@EnabledIf("com.northwind.scheduling.relay.LegacyEstateDb#available")
class LegacySingleWriterIT {

    static final Map<String, String> LOGINS = Map.of(
            "ad_portal", "P0rtal!Dev2026",
            "ad_booking_api", "B00kingApi!Dev2026",
            "ad_results", "Resu1ts!Dev2026",
            "ad_capacity_sync", "CapSync!Dev2026",
            "ad_sched_import", "Sch3dImport!Dev2026");
    static final String RELAY_PASSWORD = "Sch3dRelay!Dev2026";
    static final int PERMISSION_DENIED = 229;
    static final int COLUMN_PERMISSION_DENIED = 230;

    @BeforeAll
    static void build() {
        LegacyEstateDb.ensureApplied();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ad_portal", "ad_booking_api", "ad_results", "ad_capacity_sync", "ad_sched_import"})
    void legacyLoginCannotWriteBookingsWaitlistOrSeats(String login) throws SQLException {
        try (Connection c = LegacyEstateDb.login(login, LOGINS.get(login)); Statement st = c.createStatement()) {
            assertDenied(st, "INSERT INTO dbo.Booking (SessionId, CandidateId, Status, Channel) VALUES (154, 901, 'BOOKED', 'PORTAL')",
                    PERMISSION_DENIED);
            assertDenied(st, "UPDATE dbo.Booking SET Status = 'ATTENDED' WHERE BookingId = 1", PERMISSION_DENIED);
            assertDenied(st, "DELETE FROM dbo.Waitlist WHERE WaitlistId = 1", PERMISSION_DENIED);
            assertDenied(st, "UPDATE dbo.ExamSession SET SeatsAvailable = SeatsAvailable - 1 WHERE SessionId = 154",
                    login.equals("ad_sched_import") ? PERMISSION_DENIED : COLUMN_PERMISSION_DENIED);
            assertDenied(st, "UPDATE dbo.ExamSession SET Capacity = Capacity + 1 WHERE SessionId = 154",
                    login.equals("ad_sched_import") ? PERMISSION_DENIED : COLUMN_PERMISSION_DENIED);
            for (String proc : new String[] {"usp_BookSeat", "usp_CancelBooking", "usp_ChangeCapacity", "usp_RescheduleBooking"}) {
                assertDenied(st, "EXEC dbo." + proc, PERMISSION_DENIED);
            }
            // reads keep working (legacy pages render from the legacy tables)
            try (var rs = st.executeQuery("SELECT COUNT(*) FROM dbo.Booking")) {
                assertThat(rs.next()).isTrue();
            }
        }
    }

    @Test
    void relayLoginKeepsWriteAccess() throws SQLException {
        try (Connection c = LegacyEstateDb.login("ad_sched_relay", RELAY_PASSWORD); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            assertThat(st.executeUpdate("UPDATE dbo.ExamSession SET SeatsAvailable = SeatsAvailable WHERE SessionId = 154")).isEqualTo(1);
            st.execute("SET IDENTITY_INSERT dbo.Booking ON; INSERT INTO dbo.Booking (BookingId, SessionId, CandidateId, "
                    + "Status, Channel) VALUES (59999999, 154, 901, 'BOOKED', 'PORTAL'); SET IDENTITY_INSERT dbo.Booking OFF;");
            assertThat(st.executeUpdate("INSERT INTO dbo.RelayCheckpoint (EventId, EventSeq, EventType) VALUES (NEWID(), 1, 'X')")).isEqualTo(1);
            c.rollback();
        }
    }

    @Test
    void bookingTriggerSkipsExtraDecrementOnlyForRelayWrites() throws SQLException {
        try (Connection c = LegacyEstateDb.admin(LegacyEstateDb.DB); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            int before = seats(st, 154);
            st.execute("INSERT INTO dbo.Booking (SessionId, CandidateId, Status, Channel) VALUES (154, 902, 'BOOKED', 'PARTNER')");
            assertThat(seats(st, 154)).as("HB-04: non-PORTAL insert still decrements for legacy writers").isEqualTo(before - 1);
            st.execute("EXEC sp_set_session_context @key = N'writer', @value = N'scheduling-relay'");
            st.execute("INSERT INTO dbo.Booking (SessionId, CandidateId, Status, Channel) VALUES (154, 903, 'BOOKED', 'PARTNER')");
            assertThat(seats(st, 154)).as("relay insert: no trigger decrement").isEqualTo(before - 1);
            c.rollback();
            st.execute("EXEC sp_set_session_context @key = N'writer', @value = NULL");
        }
    }

    @Test
    void sessionTriggerSkipsDemotionAndRecomputeOnlyForRelayWrites() throws SQLException {
        try (Connection c = LegacyEstateDb.admin(LegacyEstateDb.DB); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            st.execute("EXEC sp_set_session_context @key = N'writer', @value = N'scheduling-relay'");
            int booked = count(st, "SELECT COUNT(*) FROM dbo.Booking WHERE SessionId = 154 AND Status = 'BOOKED'");
            st.execute("UPDATE dbo.ExamSession SET Capacity = 1, SeatsAvailable = 7 WHERE SessionId = 154");
            assertThat(seats(st, 154)).as("no SeatsAvailable recompute").isEqualTo(7);
            assertThat(count(st, "SELECT COUNT(*) FROM dbo.Booking WHERE SessionId = 154 AND Status = 'BOOKED'"))
                    .as("no HB-05 demotion").isEqualTo(booked);
            c.rollback();
            st.execute("EXEC sp_set_session_context @key = N'writer', @value = NULL");
            c.setAutoCommit(false);
            st.execute("UPDATE dbo.ExamSession SET Capacity = 1 WHERE SessionId = 154");
            assertThat(count(st, "SELECT COUNT(*) FROM dbo.Booking WHERE SessionId = 154 AND Status = 'BOOKED'"))
                    .as("HB-05: legacy capacity decrease still demotes").isLessThanOrEqualTo(1);
            c.rollback();
        }
    }

    private static void assertDenied(Statement st, String sql, int errorCode) {
        assertThatThrownBy(() -> st.execute(sql)).as(sql).isInstanceOfSatisfying(SQLException.class,
                e -> assertThat(e.getErrorCode()).as(e.getMessage()).isEqualTo(errorCode));
    }

    private static int seats(Statement st, int sessionId) throws SQLException {
        return count(st, "SELECT SeatsAvailable FROM dbo.ExamSession WHERE SessionId = " + sessionId);
    }

    private static int count(Statement st, String sql) throws SQLException {
        try (var rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
