package com.northwind.scheduling.importer;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.northwind.scheduling.legacy.LegacyReferenceSource;
import com.northwind.scheduling.legacy.LegacyRows;

/**
 * Copies AssessmentDelivery reference data into the Scheduling snapshots (read via
 * {@link com.northwind.scheduling.legacy.JdbcLegacyReferenceSource}, written via {@link SnapshotWriter}).
 * <ul>
 * <li>{@link Mode#MERGE}: refresh all snapshots and add sessions the Scheduling DB does not have yet.
 * The legacy seat inventory (bookings, waitlist) is copied only when the Scheduling DB has no sessions
 * at all - after that this service owns it (M4 adds the outbox back to legacy).</li>
 * <li>{@link Mode#RESET}: refresh snapshots, wipe the Scheduling inventory and copy sessions, bookings
 * and waitlist again keeping legacy ids. Used by the replay reseed script (dev/test only).</li>
 * </ul>
 */
@Service
public class SnapshotImporter {
    private static final Logger log = LoggerFactory.getLogger(SnapshotImporter.class);

    public enum Mode {
        MERGE, RESET;

        public static Mode parse(String v) {
            return v == null || v.isBlank() ? MERGE : valueOf(v.trim().toUpperCase(Locale.ROOT));
        }
    }

    private final LegacyReferenceSource source;
    private final SnapshotWriter writer;
    private final Clock clock;

    public SnapshotImporter(LegacyReferenceSource source, SnapshotWriter writer, Clock clock) {
        this.source = source;
        this.writer = writer;
        this.clock = clock;
    }

    @Transactional
    public synchronized ImportReport run(Mode mode) {
        LocalDateTime started = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        int centers = writer.upsertCenters(source.centers(), started);
        int rooms = writer.upsertRooms(source.rooms(), started);
        int exams = writer.upsertExams(source.exams(), started);
        int candidates = writer.upsertCandidates(source.candidates(), started);
        int results = writer.replaceResults(source.results(), started);

        int sessions;
        int bookings = 0;
        int waitlist = 0;
        if (mode == Mode.RESET || writer.sessionCount() == 0) {
            writer.clearInventory();
            sessions = writer.insertSessions(source.sessions(), started);
            bookings = writer.insertBookings(source.bookings());
            waitlist = writer.insertWaitlist(source.waitlist());
        } else {
            Set<Integer> have = writer.existingSessionIds();
            List<LegacyRows.Session> fresh = source.sessions().stream().filter(s -> !have.contains(s.id())).toList();
            sessions = writer.insertSessions(fresh, started);
        }
        writer.ensureBookingIdFloor();
        ImportReport report = new ImportReport(mode.name(), centers, rooms, exams, candidates, results, sessions, bookings,
                waitlist, started, LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
        writer.recordRun(report);
        log.info("Snapshot import {}", report);
        return report;
    }
}
