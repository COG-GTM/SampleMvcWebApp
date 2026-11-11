package com.northwind.scheduling.characterization.scenarios;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.northwind.scheduling.characterization.support.CharacterizationTest;
import com.northwind.scheduling.characterization.support.Scenario;

/**
 * db/triggers/032_trg_Booking_SeatSync.sql: AFTER INSERT only, decrements SeatsAvailable for BOOKED rows with
 * Channel &lt;&gt; 'PORTAL'. Needs {@code RAW_STORAGE} (rows written outside the procs).
 */
@DisplayName("032 trg_Booking_SeatSync")
class BookingSeatSyncTriggerTest extends CharacterizationTest {

    @Test
    @Scenario(id = "T32-01", covers = "032 INSERT BOOKED Channel=PARTNER -> SeatsAvailable -1 (single direct insert, no drift)", refs = "HB-04")
    void partnerInsertDecrements() {
        int s = openSession(5);
        raw().insertBooking(s, candidate(), "BOOKED", "PARTNER", null);
        assertThat(seats(s)).isEqualTo(4);
    }

    @Test
    @Scenario(id = "T32-02", covers = "032 INSERT BOOKED Channel=IMPORT -> SeatsAvailable -1", refs = "HB-04")
    void importInsertDecrements() {
        int s = openSession(5);
        raw().insertBooking(s, candidate(), "BOOKED", "IMPORT", null);
        assertThat(seats(s)).isEqualTo(4);
    }

    @Test
    @Scenario(id = "T32-03", covers = "032 INSERT with default Channel (PORTAL) -> no decrement; drift -1", refs = "HB-04")
    void defaultChannelSkipped() {
        int s = openSession(5);
        int b = raw().insertBooking(s, candidate(), "BOOKED", null, null);
        assertThat(bk(b).channel()).isEqualTo("PORTAL");
        assertThat(seats(s)).isEqualTo(5);
        expectDrift(s, -1, "HB-04 direct PORTAL-channel insert not counted by trigger 032");
    }

    @Test
    @Scenario(id = "T32-04", covers = "032 INSERT WAITLISTED Channel=PARTNER -> no change", refs = "HB-04")
    void waitlistedInsertSkipped() {
        int s = openSession(5);
        raw().insertBooking(s, candidate(), "WAITLISTED", "PARTNER", null);
        assertThat(seats(s)).isEqualTo(5);
    }

    @Test
    @Scenario(id = "T32-05", covers = "032 UPDATE WAITLISTED -> BOOKED (promotion) -> no change; drift -1", refs = "HB-04")
    void updateToBookedSkipped() {
        int s = openSession(5);
        int b = raw().insertBooking(s, candidate(), "WAITLISTED", "IMPORT", null);
        raw().updateBookingStatus(b, "BOOKED");
        assertThat(seats(s)).isEqualTo(5);
        expectDrift(s, -1, "HB-04 trigger 032 ignores UPDATE (promotion)");
    }

    @Test
    @Scenario(id = "T32-06", covers = "032 UPDATE BOOKED -> CANCELLED -> no change; drift +1", refs = "HB-04")
    void updateToCancelledSkipped() {
        int s = openSession(5);
        int b = bookOk(s, candidate());
        raw().updateBookingStatus(b, "CANCELLED");
        assertThat(seats(s)).isEqualTo(4);
        expectDrift(s, 1, "HB-04 trigger 032 ignores UPDATE (direct cancel)");
    }

    @Test
    @Scenario(id = "T32-07", covers = "032 DELETE of a BOOKED row -> no change; drift +1", refs = "HB-04")
    void deleteSkipped() {
        int s = openSession(5);
        int b = bookOk(s, candidate());
        raw().deleteBooking(b);
        assertThat(seats(s)).isEqualTo(4);
        expectDrift(s, 1, "HB-04 trigger 032 ignores DELETE");
    }

    @Test
    @Scenario(id = "T32-08", covers = "032 multi-row INSERT is grouped per SessionId (-2 / -1)", refs = "HB-04")
    void multiRowInsertGrouped() {
        int s1 = openSession(5);
        int s2 = openSession(5);
        List<Integer> ids = raw().insertBookings(List.of(new int[] {s1, candidate()}, new int[] {s1, candidate()},
                new int[] {s2, candidate()}), "BOOKED", "IMPORT");
        assertThat(ids).hasSize(3);
        assertThat(seats(s1)).isEqualTo(3);
        assertThat(seats(s2)).isEqualTo(4);
    }

    @Test
    @Scenario(id = "T32-09", covers = "032 no floor: import into a full session drives SeatsAvailable negative", refs = "HB-04, HB-28")
    void importIntoFullGoesNegative() {
        int s = openSession(1);
        bookOk(s, candidate());
        raw().insertBooking(s, candidate(), "BOOKED", "IMPORT", null);
        assertThat(seats(s)).isEqualTo(-1);
        assertThat(countStatus(s, "BOOKED")).isEqualTo(2);
    }

    @Test
    @Scenario(id = "T32-10", covers = "032 + booking-api decrement: a partner booking takes two seats (drift +1)", refs = "HB-01, MR-01")
    void partnerDoubleDecrement() {
        int s = openSession(10);
        int b = raw().partnerBook(s, candidate(), null);
        assertThat(bk(b).channel()).isEqualTo("PARTNER");
        assertThat(seats(s)).isEqualTo(8);
        expectDrift(s, 1, "HB-01 partner booking double decrement (trigger 032 + booking-api)");
    }

    @Test
    @Scenario(id = "T32-11", covers = "032 skips usp_BookSeat inserts (Channel=PORTAL): portal booking moves the counter by exactly 1", refs = "HB-01")
    void portalProcSingleDecrement() {
        int s = openSession(10);
        bookOk(s, candidate());
        bookOk(s, candidate());
        assertThat(seats(s)).isEqualTo(8);
    }
}
