package com.northwind.scheduling.web;

import com.northwind.scheduling.api.ApiException;
import com.northwind.scheduling.core.Demotion;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy direct-write compatibility API (contract addition, off by default: scheduling.storage-api.enabled).
 *
 * Reproduces what direct SQL against dbo.Booking / dbo.ExamSession did in AssessmentDelivery, including the two
 * triggers, now explicit: trg_Booking_SeatSync (BOOKED non-PORTAL inserts decrement SeatsAvailable, HB-04) and
 * trg_ExamSession_Update (every session update stamps LastModified; a capacity cut demotes, HB-05). Used by the
 * characterization suite's RAW_STORAGE scenarios and by Phase 3 dual-run replays of direct writers. Not for clients.
 */
@RestController
@RequestMapping("/api/v1/admin/storage")
@ConditionalOnProperty(name = "scheduling.storage-api.enabled", havingValue = "true")
public class StorageController {

    public record BookingRow(Integer sessionId, Integer candidateId, String status, String channel, String accommodationCode) {
    }

    public record BookingInsert(List<BookingRow> rows) {
    }

    public record BookingPatch(String status, LocalDateTime bookedAt) {
    }

    public record SeatsDelta(Integer delta) {
    }

    public record SessionPatch(Integer sessionId, Integer capacity, String status, LocalDateTime lastModified) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Demotion demotion;

    public StorageController(JdbcTemplate jdbc, TransactionTemplate tx, Demotion demotion) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.demotion = demotion;
    }

    /** One multi-row INSERT statement, then the trg_Booking_SeatSync accounting for the whole statement. */
    @PostMapping("/bookings")
    public Map<String, Object> insertBookings(@RequestBody BookingInsert body) {
        if (body == null || body.rows() == null || body.rows().isEmpty()) {
            throw ApiException.badRequest("VALIDATION", "rows are required");
        }
        List<Integer> ids = tx.execute(s -> {
            StringBuilder sql = new StringBuilder("INSERT INTO dbo.Booking (SessionId, CandidateId, Status, AccommodationCode, Channel) "
                    + "OUTPUT INSERTED.BookingId VALUES ");
            List<Object> args = new ArrayList<>();
            for (int i = 0; i < body.rows().size(); i++) {
                BookingRow r = body.rows().get(i);
                sql.append(i == 0 ? "" : ", ").append("(?, ?, ?, ?, ISNULL(?, 'PORTAL'))");
                args.add(r.sessionId());
                args.add(r.candidateId());
                args.add(r.status());
                args.add(r.accommodationCode());
                args.add(r.channel());
            }
            List<Integer> inserted = new ArrayList<>(jdbc.queryForList(sql.toString(), Integer.class, args.toArray()));
            inserted.sort(Integer::compare);
            Map<Integer, Integer> taken = new LinkedHashMap<>();
            for (BookingRow r : body.rows()) {
                if ("BOOKED".equals(r.status()) && r.channel() != null && !"PORTAL".equals(r.channel())) {
                    taken.merge(r.sessionId(), 1, Integer::sum);
                }
            }
            taken.forEach((sessionId, n) -> jdbc.update("UPDATE dbo.ExamSession SET SeatsAvailable = SeatsAvailable - ?, "
                    + "LastModified = GETDATE() WHERE SessionId = ?", n, sessionId));
            return inserted;
        });
        return Map.of("bookingIds", ids);
    }

    @PatchMapping("/bookings/{bookingId}")
    public ResponseEntity<Void> patchBooking(@PathVariable int bookingId, @RequestBody BookingPatch p) {
        tx.executeWithoutResult(s -> {
            if (p.status() != null) {
                jdbc.update("UPDATE dbo.Booking SET Status = ?, LastModified = GETDATE() WHERE BookingId = ?", p.status(), bookingId);
            }
            if (p.bookedAt() != null) {
                jdbc.update("UPDATE dbo.Booking SET BookedAt = ? WHERE BookingId = ?", p.bookedAt(), bookingId);
            }
        });
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/bookings/{bookingId}")
    public ResponseEntity<Void> deleteBooking(@PathVariable int bookingId) {
        tx.executeWithoutResult(s -> {
            jdbc.update("DELETE FROM dbo.Waitlist WHERE BookingId = ?", bookingId);
            jdbc.update("DELETE FROM dbo.Booking WHERE BookingId = ?", bookingId);
        });
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/{sessionId}/seats")
    public ResponseEntity<Void> adjustSeats(@PathVariable int sessionId, @RequestBody SeatsDelta d) {
        jdbc.update("UPDATE dbo.ExamSession SET SeatsAvailable = SeatsAvailable + ?, LastModified = GETDATE() WHERE SessionId = ?",
                d.delta(), sessionId);
        return ResponseEntity.noContent().build();
    }

    /** One UPDATE statement over several sessions, then trg_ExamSession_Update for every row it touched. */
    @PatchMapping("/sessions")
    public ResponseEntity<Void> patchSessions(@RequestBody List<SessionPatch> patches) {
        tx.executeWithoutResult(s -> {
            for (SessionPatch p : patches) {
                List<Integer> old = jdbc.queryForList("SELECT Capacity FROM dbo.ExamSession WITH (UPDLOCK, HOLDLOCK) WHERE SessionId = ?",
                        Integer.class, p.sessionId());
                if (old.isEmpty()) {
                    continue;
                }
                if (p.capacity() != null) {
                    jdbc.update("UPDATE dbo.ExamSession SET Capacity = ? WHERE SessionId = ?", p.capacity(), p.sessionId());
                }
                if (p.status() != null) {
                    jdbc.update("UPDATE dbo.ExamSession SET Status = ? WHERE SessionId = ?", p.status(), p.sessionId());
                }
                if (p.lastModified() != null) {
                    jdbc.update("UPDATE dbo.ExamSession SET LastModified = ? WHERE SessionId = ?", p.lastModified(), p.sessionId());
                }
                // trigger 033: LastModified is always overwritten, then the capacity-cut demotion
                jdbc.update("UPDATE dbo.ExamSession SET LastModified = GETDATE() WHERE SessionId = ?", p.sessionId());
                if (p.capacity() != null) {
                    demotion.afterCapacityChange(p.sessionId(), old.get(0), p.capacity());
                }
            }
        });
        return ResponseEntity.noContent().build();
    }
}
