# Parity report: legacy `legacy-baseline` vs modern estate (MBA-3825)

- **Tested code SHA** (scheduling-service, routing-gateway, harness): `ad48c47`
- Legacy side: tag `legacy-baseline` (commit `e458238da39c991b51d8f670f6d0c463b51b372f`), replayed directly (:8080/:8081/:8083) with the harness copied from `ad48c47`
- Modern side: `scripts/run-legacy.ps1` + `run-scheduling-service.ps1` + `run-routing-gateway.ps1` at the tested SHA, every request through the gateway `http://localhost:8000`
- Transcripts: the 64 M1 transcripts in `characterization/transcripts/`, unchanged
- Matching = same HTTP status, business fields and Booking/Waitlist/ExamSession/AuditLog state after normalization (`characterization/harness/normalize.py`, `compare.py`, `--compare all`). Expected value = M1 legacy transcript, actual value = replay.
- Raw replays: [replay-legacy-ad48c47.md](replay-legacy-ad48c47.md), [replay-modern-ad48c47.md](replay-modern-ad48c47.md)

Commands:

```
# legacy (tag worktree)
powershell -ExecutionPolicy Bypass -File scripts/run-legacy.ps1 < /dev/null
"$BASH" characterization/replay --target legacy --report replay-legacy.md
# modern (branch worktree at the tested SHA)
powershell -ExecutionPolicy Bypass -File scripts/run-legacy.ps1 < /dev/null    # reseeds AssessmentDelivery
sqlcmd ... BACKUP DATABASE [AssessmentDelivery] TO DISK = '...AssessmentDelivery_scheduling_seed.bak'  # fresh-seed backup for RESEED_BACKUP_PATH
powershell -ExecutionPolicy Bypass -File scripts/run-scheduling-service.ps1 -SkipBuild < /dev/null
powershell -ExecutionPolicy Bypass -File scripts/run-routing-gateway.ps1 -SkipBuild < /dev/null
RESEED_BACKUP_PATH=<seed .bak> "$BASH" characterization/replay --target modern --base-url http://localhost:8000 \
  --reseed command --reseed-cmd '"C:\Program Files\Git\bin\bash.exe" services/scheduling-service/scripts/reseed-scheduling-db.sh' \
  --settle-url http://localhost:8090/internal/relay --compare all --report replay-modern.md
```

`reseed-scheduling-db.sh` restores AssessmentDelivery from the fresh-seed backup and then runs the `mode=reset` snapshot import, so both databases start every scenario from the same consistent seed (runbook `docs/runbooks/scheduling-service.md`). `--settle-url` waits until the outbox relay has projected every event before each snapshot.

## Summary per scenario group

| Group | Transcripts | Legacy = M1 | Modern = M1 | Modern differences, classified | Unclassified (defects) |
|---|---|---|---|---|---|
| Booking | 11 | 11/11 | 2/11 | 9/9 | 0 |
| Cancellation | 8 | 8/8 | 0/8 | 8/8 | 0 |
| Reschedule | 10 | 10/10 | 0/10 | 10/10 | 0 |
| Capacity increase/decrease | 9 | 9/9 | 0/9 | 9/9 | 0 |
| Retake eligibility | 15 | 15/15 | 13/15 | 2/2 | 0 |
| &nbsp;&nbsp;retake, both channel | 13 | 13/13 | 13/13 | 0/0 | 0 |
| &nbsp;&nbsp;retake, portal channel | 1 | 1/1 | 0/1 | 1/1 | 0 |
| &nbsp;&nbsp;retake, api channel | 1 | 1/1 | 0/1 | 1/1 | 0 |
| Accommodation | 8 | 8/8 | 1/8 | 7/7 | 0 |
| Mixed traffic | 3 | 3/3 | 0/3 | 3/3 | 0 |
| **Total** | **64** | **64/64** | **16/64** | **48/48** | **0** |

Unclassified mismatches (defects): **none**. Every modern difference is listed below by transcript id with the ADR (0002 ported rules, 0003 single writer, 0004 retired URLs, 0005 auto-offer) or `ODDITIES.md` entry (decided in ADR 0002) that allows it. Each individual difference line is assigned to a rule; a transcript is unclassified if any line matches no rule.

## Booking

| Transcript | Channel | Legacy | Modern | Allowed difference |
|---|---|---|---|---|
| `BK-01-portal-book-open-session` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `BK-02-api-book-open-session` | api | match | differs | [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone; [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`) |
| `BK-03-portal-book-full-session` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `BK-04-api-book-full-session` | api | match | differs | [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`) |
| `BK-05-portal-duplicate-booking` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `BK-06-api-duplicate-booking` | api | match | differs | [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`); [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone |
| `BK-07-db-duplicate-and-closed-error-codes` | db | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ); [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PROC-DROPPED (proc dropped: SQL error 2812 instead of 50000) |
| `BK-08-portal-book-closed-session` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `BK-09-api-book-closed-session` | api | match | match | - |
| `BK-10-portal-book-cancelled-session` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `BK-11-api-book-cancelled-session` | api | match | match | - |

## Cancellation

| Transcript | Channel | Legacy | Modern | Allowed difference |
|---|---|---|---|---|
| `CN-01-portal-cancel-booked` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `CN-02-api-cancel-booked-partner` | api | match | differs | [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`); [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone |
| `CN-03-portal-cancel-waitlisted` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `CN-04-api-cancel-waitlisted` | api | match | differs | [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`) |
| `CN-05-portal-cancel-on-full-session-no-promotion` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `CN-06-api-cancel-on-full-session-no-promotion` | api | match | differs | [ADR 0005](../../docs/adr/0005-waitlist-auto-offer.md) decisions 1, 3, 7 (the freed seat is offered to waitlist position 1: `WAITLIST_OFFER` audit row; the offered booking stays `WAITLISTED` in legacy, its row is re-projected so `LastModified` is stamped) - supersedes ODD-09 for BOOKED cancels |
| `CN-07-portal-cancel-twice` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `CN-08-api-cancel-twice` | api | match | differs | [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`); [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone |

## Reschedule

| Transcript | Channel | Legacy | Modern | Allowed difference |
|---|---|---|---|---|
| `RS-01-portal-reschedule-same-exam` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `RS-02-api-reschedule-same-exam` | api | match | differs | [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`); [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone; [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-05 (reschedule = cancel + book; one `BookingRescheduled` event instead of CANCEL + BOOK rows) |
| `RS-03-portal-reschedule-different-exam` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `RS-04-api-reschedule-different-exam` | api | match | differs | [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`); [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone |
| `RS-05-portal-reschedule-same-session` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `RS-06-api-reschedule-same-session` | api | match | differs | [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`); [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone |
| `RS-07-db-reschedule-different-exam-error` | db | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ); [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PROC-DROPPED (proc dropped: SQL error 2812 instead of 50000) |
| `RS-08-portal-reschedule-partner-booking` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `RS-09-api-reschedule-to-full-session` | api | match | differs | [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`); [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone; [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-05 (reschedule = cancel + book; one `BookingRescheduled` event instead of CANCEL + BOOK rows) |
| `RS-10-portal-reschedule-to-full-session` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |

## Capacity increase/decrease

| Transcript | Channel | Legacy | Modern | Allowed difference |
|---|---|---|---|---|
| `CP-01-portal-capacity-increase` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `CP-02-portal-capacity-decrease-above-active` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `CP-03-portal-capacity-decrease-below-active` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `CP-04-portal-capacity-raise-then-lower` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `CP-05-portal-capacity-below-accommodation` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `CP-06-db-capacity-below-accommodation-error` | db | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PROC-DROPPED (proc dropped: SQL error 2812 instead of 50000) |
| `CP-07-portal-capacity-decrease-demotes-newest-mixed-channels` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `CP-08-portal-capacity-equal-to-accommodation` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `CP-09-db-capacity-decrease-below-active` | db | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PROC-DROPPED (proc dropped: SQL error 2812 instead of 50000) |

## Retake eligibility (per channel)

| Transcript | Channel | Legacy | Modern | Allowed difference |
|---|---|---|---|---|
| `RT-01-eligibility-nw00000113-2026-11-20` | both | match | match | - |
| `RT-02-eligibility-nw00000113-2026-12-06` | both | match | match | - |
| `RT-03-eligibility-nw00000113-2026-12-07` | both | match | match | - |
| `RT-04-eligibility-nw00000113-2026-12-12` | both | match | match | - |
| `RT-05-eligibility-nw00000113-2026-12-17` | both | match | match | - |
| `RT-06-eligibility-nw00000113-2026-12-18` | both | match | match | - |
| `RT-07-eligibility-nw00000113-2026-12-21` | both | match | match | - |
| `RT-08-eligibility-nw00000950-2026-11-27` | both | match | match | - |
| `RT-09-eligibility-nw00000950-2026-11-29` | both | match | match | - |
| `RT-10-eligibility-nw00000950-2026-11-30` | both | match | match | - |
| `RT-11-eligibility-nw00000950-2026-12-11` | both | match | match | - |
| `RT-12-eligibility-nw00000362-2026-12-21` | both | match | match | - |
| `RT-13-eligibility-nw00000901-2026-11-20` | both | match | match | - |
| `RT-14-portal-book-retake-between-boundaries` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `RT-15-api-book-retake-between-boundaries` | api | match | differs | [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-04 (calendar retake days, portal rule): booking-api's working-day answer `NOT_ELIGIBLE ... 2026-12-18` becomes eligible on 2026-12-07, so the booking is taken |

## Accommodation

| Transcript | Channel | Legacy | Modern | Allowed difference |
|---|---|---|---|---|
| `AC-01-portal-extra-time-over-allocation` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `AC-02-api-extra-time-over-allocation` | api | match | differs | [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`); [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone |
| `AC-03-portal-sr-rb-do-not-consume-accommodation` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `AC-04-api-sr-rb-do-not-consume-accommodation` | api | match | differs | [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`); [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone |
| `AC-05-portal-blank-accommodation-uses-profile` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `AC-06-api-blank-accommodation-is-none` | api | match | differs | [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone; [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`) |
| `AC-07-portal-extra-time-on-zero-allocation` | portal | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ) |
| `AC-08-api-extra-time-on-zero-allocation` | api | match | match | - |

## Mixed traffic

| Transcript | Channel | Legacy | Modern | Allowed difference |
|---|---|---|---|---|
| `MX-01-mixed-traffic-then-capacity-sync` | both | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-MIXED (portal bookings through :8000 complete via `/app` = `POST /api/v2/bookings`), [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (one seat per booking, so fewer bookings are waitlisted), [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §8 (capacity-sync `SKIPPED`) |
| `MX-02-capacity-sync-on-fresh-seed` | sync | match | differs | [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §8/§9 (capacity-sync disabled, `SKIPPED`; reconciliation replaces it) and [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-03 (one seat formula, so 0 drift on the fresh seed) |
| `MX-03-capacity-sync-with-accommodation-bookings` | both | match | differs | [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ); [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §8 (capacity-sync `SKIPPED`) and [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-03/ODD-13 (0 drift before and after) |

## Mismatch details

For every modern transcript that differs from the M1 transcript: the requests, then each difference as `path: expected -> actual` (expected = legacy M1 transcript, actual = modern replay through :8000).

### AC-01-portal-extra-time-over-allocation

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 163, "CandidateNumber": "NW00000932", "AccommodationCode": "ET25"}}`
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 163, "CandidateNumber": "NW00000903", "AccommodationCode": "ET50"}}`

```
$.before.audit: length 1 -> 0
$.before.bookings: length 4 -> 3
$.before.sessions[0].BookedOrAttended: 4 -> 3
$.before.sessions[0].ExtraTimeBooked: 1 -> 0
$.before.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.before.sessions[0].SeatsAvailable: 4 -> 5
$.steps[0].response.path: '/Booking/Book' -> '/app/book?sessionId=163'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Centre': 'Thameside Assessment Centre (LDN01)', 'Room': 'S...)
$.steps[0].response.rendered.heading: 'Book a seat Session #163' -> None
$.steps[0].sessions_after[0].SeatsAvailable: 4 -> 5
$.after.audit: length 1 -> 0
$.after.bookings: length 4 -> 3
$.after.sessions[0].BookedOrAttended: 4 -> 3
$.after.sessions[0].ExtraTimeBooked: 1 -> 0
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 4 -> 5
```

### AC-02-api-extra-time-over-allocation

- allowed by: [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`); [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone
- requests:
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 163, "candidateNumber": "NW00000933", "accommodationCode": "ET25"}}`
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 163, "candidateNumber": "NW00000934", "accommodationCode": "ET50"}}`

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=163 status=BOOKED channel=PARTNER' -> 'outbox seq=77 event=27fbdcb8-b1cc-4f1b-aad9-9ae5caad72ae BookingCreated channel=PARTNER'
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 3 -> 4
$.steps[0].sessions_after[0].SeatsAvailable: 3 -> 4
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=163 status=BOOKED channel=PARTNER' -> 'outbox seq=77 event=27fbdcb8-b1cc-4f1b-aad9-9ae5caad72ae BookingCreated channel=PARTNER'
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[0].SeatsAvailable: 3 -> 4
```

### AC-03-portal-sr-rb-do-not-consume-accommodation

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 163, "CandidateNumber": "NW00000935", "AccommodationCode": "ET50"}}`
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 163, "CandidateNumber": "NW00000923", "AccommodationCode": "SR"}}`
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 163, "CandidateNumber": "NW00000943", "AccommodationCode": "RB"}}`

```
$.before.audit: length 1 -> 0
$.before.bookings: length 4 -> 3
$.before.sessions[0].BookedOrAttended: 4 -> 3
$.before.sessions[0].ExtraTimeBooked: 1 -> 0
$.before.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.before.sessions[0].SeatsAvailable: 4 -> 5
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000923' -> '/app/book?sessionId=163'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Arjun Whitlock', 'Candidate no.': 'NW00000923', 'Email': 'arjun.whitlock923@example.org', 'Default accommod...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.steps[0].sessions_after[0].SeatsAvailable: 3 -> 5
$.steps[1].response.path: '/Booking/MyBookings?candidateNumber=NW00000943' -> '/app/book?sessionId=163'
$.steps[1].response.rendered.alerts: length 1 -> 0
$.steps[1].response.rendered.cells: missing in replay (expected {'Name': 'Oliver Castell', 'Candidate no.': 'NW00000943', 'Email': 'oliver.castell943@example.net', 'Default accommod...)
$.steps[1].response.rendered.heading: 'My bookings' -> None
$.steps[1].sessions_after[0].SeatsAvailable: 2 -> 5
$.after.audit: length 3 -> 0
$.after.bookings: length 6 -> 3
$.after.sessions[0].BookedOrAttended: 6 -> 3
$.after.sessions[0].ExtraTimeBooked: 1 -> 0
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 2 -> 5
```

### AC-04-api-sr-rb-do-not-consume-accommodation

- allowed by: [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`); [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone
- requests:
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 163, "candidateNumber": "NW00000936", "accommodationCode": "ET50"}}`
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 163, "candidateNumber": "NW00000937", "accommodationCode": "SR"}}`
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 163, "candidateNumber": "NW00000938", "accommodationCode": "RB"}}`

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=163 status=BOOKED channel=PARTNER' -> 'outbox seq=80 event=8531fae3-6f9d-4c5e-a8dc-5581f4be7651 BookingCreated channel=PARTNER'
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 3 -> 4
$.steps[0].sessions_after[0].SeatsAvailable: 1 -> 3
$.steps[1].sessions_after[0].SeatsAvailable: -1 -> 2
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=163 status=BOOKED channel=PARTNER' -> 'outbox seq=80 event=8531fae3-6f9d-4c5e-a8dc-5581f4be7651 BookingCreated channel=PARTNER'
$.after.audit[1].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[1].Detail: 'session=163 status=BOOKED channel=PARTNER' -> 'outbox seq=81 event=eb06dc4b-481e-44f2-a797-f5610aab1da2 BookingCreated channel=PARTNER'
$.after.audit[2].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[2].Detail: 'session=163 status=BOOKED channel=PARTNER' -> 'outbox seq=82 event=15276c9c-a8b2-494e-a431-5d450621f001 BookingCreated channel=PARTNER'
$.after.sessions[0].SeatDrift: -3 -> 0
$.after.sessions[0].SeatsAvailable: -1 -> 2
```

### AC-05-portal-blank-accommodation-uses-profile

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 154, "CandidateNumber": "NW00000003", "AccommodationCode": ""}}`

```
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000003' -> '/app/book?sessionId=154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Arjun Pryce', 'Candidate no.': 'NW00000003', 'Email': 'arjun.pryce3@example.com', 'Default accommodation': ...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.steps[0].sessions_after[0].SeatsAvailable: 10 -> 11
$.after.audit: length 1 -> 0
$.after.bookings: length 12 -> 11
$.after.bookings[10].BookedAt: '<now>' -> '2026-11-09T00:27:00'
$.after.bookings[10].BookingId: '<booking:1>' -> 2114
$.after.bookings[10].LastModified: '<now>' -> '2026-11-09T00:27:00'
$.after.bookings[10].SessionId: 154 -> 245
$.after.sessions[0].BookedOrAttended: 10 -> 9
$.after.sessions[0].ExtraTimeBooked: 1 -> 0
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 10 -> 11
```

### AC-06-api-blank-accommodation-is-none

- allowed by: [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone; [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`)
- requests:
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 154, "candidateNumber": "NW00000003"}}`

```
$.steps[0].sessions_after[0].SeatsAvailable: 9 -> 10
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER' -> 'outbox seq=85 event=4ce07568-cccc-4f29-95b9-70173af385da BookingCreated channel=PARTNER'
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[0].SeatsAvailable: 9 -> 10
```

### AC-07-portal-extra-time-on-zero-allocation

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 155, "CandidateNumber": "NW00000939", "AccommodationCode": "ET25"}}`

```
$.steps[0].response.path: '/Booking/Book' -> '/app/book?sessionId=155'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Exam': 'HPI-ACC2 – Management Accounting', 'Centre': 'Liffey Assessment Rooms (DUB01)', 'Room': 'Room 1', 'Start': ...)
$.steps[0].response.rendered.heading: 'Book a seat Session #155' -> None
```

### BK-01-portal-book-open-session

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 154, "CandidateNumber": "NW00000901", "AccommodationCode": ""}}`

```
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000901' -> '/app/book?sessionId=154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Ryan Pennington', 'Candidate no.': 'NW00000901', 'Email': 'ryan.pennington901@example.net', 'Default accomm...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.steps[0].sessions_after[0].SeatsAvailable: 10 -> 11
$.after.audit: length 1 -> 0
$.after.bookings: length 11 -> 10
$.after.sessions[0].BookedOrAttended: 10 -> 9
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 10 -> 11
```

### BK-02-api-book-open-session

- allowed by: [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone; [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`)
- requests:
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 154, "candidateNumber": "NW00000902", "externalRef": "PX-1001"}}`

```
$.steps[0].sessions_after[0].SeatsAvailable: 9 -> 10
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER ref=PX-1001' -> 'outbox seq=4 event=f72bf297-9ba9-4933-a6db-6bc609209f2a BookingCreated channel=PARTNER'
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[0].SeatsAvailable: 9 -> 10
```

### BK-03-portal-book-full-session

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 153, "CandidateNumber": "NW00000904", "AccommodationCode": ""}}`

```
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000904' -> '/app/book?sessionId=153'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Chloe Okafor', 'Candidate no.': 'NW00000904', 'Email': 'chloe.okafor904@example.net', 'Default accommodatio...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.after.audit: length 1 -> 0
$.after.bookings: length 26 -> 25
$.after.sessions[0].Waitlisted: 4 -> 3
$.after.waitlist: length 4 -> 3
```

### BK-04-api-book-full-session

- allowed by: [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`)
- requests:
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 153, "candidateNumber": "NW00000905"}}`

```
$.after.audit[0].Action: 'BOOK' -> 'WAITLIST'
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=153 status=WAITLISTED channel=PARTNER' -> 'outbox seq=7 event=6d5b6628-59fd-4259-b674-e65ee7b0727d BookingWaitlisted channel=PARTNER'
```

### BK-05-portal-duplicate-booking

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 154, "CandidateNumber": "NW00000906", "AccommodationCode": ""}}`
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 154, "CandidateNumber": "NW00000906", "AccommodationCode": ""}}`

```
$.before.audit: length 1 -> 0
$.before.bookings: length 11 -> 10
$.before.sessions[0].BookedOrAttended: 10 -> 9
$.before.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.before.sessions[0].SeatsAvailable: 10 -> 11
$.steps[0].response.path: '/Booking/Book' -> '/app/book?sessionId=154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Centre': 'Bayside Assessment Centre (SFO01)', 'Room': 'Roo...)
$.steps[0].response.rendered.heading: 'Book a seat Session #154' -> None
$.steps[0].sessions_after[0].SeatsAvailable: 10 -> 11
$.after.audit: length 1 -> 0
$.after.bookings: length 11 -> 10
$.after.sessions[0].BookedOrAttended: 10 -> 9
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 10 -> 11
```

### BK-06-api-duplicate-booking

- allowed by: [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`); [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone
- requests:
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 154, "candidateNumber": "NW00000907"}}`
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 154, "candidateNumber": "NW00000907"}}`

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER' -> 'outbox seq=10 event=7a1a75d6-b4ce-47c2-b541-ace0f490c079 BookingCreated channel=PARTNER'
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 9 -> 10
$.steps[0].sessions_after[0].SeatsAvailable: 9 -> 10
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER' -> 'outbox seq=10 event=7a1a75d6-b4ce-47c2-b541-ace0f490c079 BookingCreated channel=PARTNER'
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[0].SeatsAvailable: 9 -> 10
```

### BK-07-db-duplicate-and-closed-error-codes

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ); [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PROC-DROPPED (proc dropped: SQL error 2812 instead of 50000)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 154, "CandidateNumber": "NW00000908", "AccommodationCode": ""}}`
  - db_proc: `{"channel": "db", "sql": "DECLARE @c INT = (SELECT CandidateId FROM dbo.Candidate WHERE CandidateNumber = 'NW00000908'), @b INT, @s VARCHAR(20); EXEC dbo.usp_BookSeat @SessionId = 154, @CandidateId = @c, @AccommodationCode = NULL, @BookingId = @b OUTPUT, @Resu...`
  - db_proc: `{"channel": "db", "sql": "DECLARE @c INT = (SELECT CandidateId FROM dbo.Candidate WHERE CandidateNumber = 'NW00000908'), @b INT, @s VARCHAR(20); EXEC dbo.usp_BookSeat @SessionId = 263, @CandidateId = @c, @AccommodationCode = NULL, @BookingId = @b OUTPUT, @Resu...`

```
$.before.audit: length 1 -> 0
$.before.bookings: length 13 -> 12
$.before.bookings[10].BookedAt: '<now>' -> '2026-11-09T19:51:00'
$.before.bookings[10].BookingId: '<booking:1>' -> 2217
$.before.bookings[10].CandidateNumber: 'NW00000908' -> 'NW00000201'
$.before.bookings[10].LastModified: '<now>' -> '2026-11-09T19:51:00'
$.before.bookings[10].SessionId: 154 -> 263
$.before.bookings[11].BookedAt: '2026-11-09T19:51:00' -> '2026-11-10T05:22:00'
$.before.bookings[11].BookingId: 2217 -> 2218
$.before.bookings[11].CandidateNumber: 'NW00000201' -> 'NW00000610'
$.before.bookings[11].Channel: 'PORTAL' -> 'PARTNER'
$.before.bookings[11].ExternalRef: None -> 'PTR-0026302'
$.before.bookings[11].LastModified: '2026-11-09T19:51:00' -> '2026-11-10T05:22:00'
$.before.sessions[0].BookedOrAttended: 10 -> 9
$.before.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.before.sessions[0].SeatsAvailable: 10 -> 11
$.steps[0].response.errors[0].message: 'Candidate 908 already has an active booking on session 154.' -> "Could not find stored procedure 'dbo.usp_BookSeat'."
$.steps[0].response.errors[0].number: 50000 -> 2812
$.steps[0].response.errors[0].state: 2 -> 62
$.steps[0].sessions_after[0].SeatsAvailable: 10 -> 11
$.steps[1].response.errors[0].message: 'Session 263 is not open for booking.' -> "Could not find stored procedure 'dbo.usp_BookSeat'."
$.steps[1].response.errors[0].number: 50000 -> 2812
$.steps[1].response.errors[0].state: 1 -> 62
$.steps[1].sessions_after[0].SeatsAvailable: 10 -> 11
$.after.audit: length 1 -> 0
$.after.bookings: length 13 -> 12
$.after.bookings[10].BookedAt: '<now>' -> '2026-11-09T19:51:00'
$.after.bookings[10].BookingId: '<booking:1>' -> 2217
$.after.bookings[10].CandidateNumber: 'NW00000908' -> 'NW00000201'
$.after.bookings[10].LastModified: '<now>' -> '2026-11-09T19:51:00'
$.after.bookings[10].SessionId: 154 -> 263
$.after.bookings[11].BookedAt: '2026-11-09T19:51:00' -> '2026-11-10T05:22:00'
$.after.bookings[11].BookingId: 2217 -> 2218
$.after.bookings[11].CandidateNumber: 'NW00000201' -> 'NW00000610'
$.after.bookings[11].Channel: 'PORTAL' -> 'PARTNER'
$.after.bookings[11].ExternalRef: None -> 'PTR-0026302'
$.after.bookings[11].LastModified: '2026-11-09T19:51:00' -> '2026-11-10T05:22:00'
$.after.sessions[0].BookedOrAttended: 10 -> 9
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 10 -> 11
```

### BK-08-portal-book-closed-session

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 263, "CandidateNumber": "NW00000909", "AccommodationCode": ""}}`

```
$.steps[0].response.path: '/Booking/Book' -> '/app/book?sessionId=263'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Exam': 'HPI-ACC1 – Foundations of Financial Accounting', 'Centre': 'Midtown Assessment Suites (NYC02)', 'Room': 'Su...)
$.steps[0].response.rendered.heading: 'Book a seat Session #263' -> None
```

### BK-10-portal-book-cancelled-session

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 162, "CandidateNumber": "NW00000911", "AccommodationCode": ""}}`

```
$.steps[0].response.path: '/Booking/Book' -> '/app/book?sessionId=162'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Exam': 'BCB-AGL – Agile Delivery Essentials', 'Centre': 'Peachtree Exam Centre (ATL01)', 'Room': 'Room 102', 'Start...)
$.steps[0].response.rendered.heading: 'Book a seat Session #162' -> None
```

### CN-01-portal-cancel-booked

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 154, "CandidateNumber": "NW00000913", "AccommodationCode": ""}}`
  - portal_cancel: `{"channel": "portal", "method": "POST", "path": "/Booking/Cancel/<booking:1>", "form": {"CandidateNumber": "NW00000913", "Reason": "changed plans"}}`

```
replay ERROR: RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=154', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

### CN-02-api-cancel-booked-partner

- allowed by: [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`); [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone
- requests:
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 154, "candidateNumber": "NW00000914", "externalRef": "PX-2001"}}`
  - api_cancel: `{"channel": "api", "method": "DELETE", "path": "/api/v1/bookings/<booking:1>?reason=partner-cancel"}`

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER ref=PX-2001' -> 'outbox seq=18 event=bc6ce96c-d620-492a-9bec-9d7fb6803b47 BookingCreated channel=PARTNER'
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 9 -> 10
$.steps[0].sessions_after[0].SeatsAvailable: 10 -> 11
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER ref=PX-2001' -> 'outbox seq=18 event=bc6ce96c-d620-492a-9bec-9d7fb6803b47 BookingCreated channel=PARTNER'
$.after.audit[1].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[1].Detail: 'partner-cancel' -> 'outbox seq=19 event=272b7d30-523e-4a5a-a643-b074029561b8 BookingCancelled reason=partner-cancel'
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[0].SeatsAvailable: 10 -> 11
```

### CN-03-portal-cancel-waitlisted

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 153, "CandidateNumber": "NW00000915", "AccommodationCode": ""}}`
  - portal_cancel: `{"channel": "portal", "method": "POST", "path": "/Booking/Cancel/<booking:1>", "form": {"CandidateNumber": "NW00000915", "Reason": "characterization"}}`

```
replay ERROR: RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=153', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

### CN-04-api-cancel-waitlisted

- allowed by: [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`)
- requests:
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 153, "candidateNumber": "NW00000916"}}`
  - api_cancel: `{"channel": "api", "method": "DELETE", "path": "/api/v1/bookings/<booking:1>"}`

```
$.before.audit[0].Action: 'BOOK' -> 'WAITLIST'
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=153 status=WAITLISTED channel=PARTNER' -> 'outbox seq=22 event=b0d12977-0ffb-49cf-86e9-66d2a14b6135 BookingWaitlisted channel=PARTNER'
$.after.audit[0].Action: 'BOOK' -> 'WAITLIST'
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=153 status=WAITLISTED channel=PARTNER' -> 'outbox seq=22 event=b0d12977-0ffb-49cf-86e9-66d2a14b6135 BookingWaitlisted channel=PARTNER'
$.after.audit[1].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[1].Detail: 'Cancelled by partner' -> 'outbox seq=23 event=5c8daa98-dcc8-472f-9e0a-f492fa8f177b BookingCancelled reason=Cancelled by partner'
```

### CN-05-portal-cancel-on-full-session-no-promotion

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_cancel: `{"channel": "portal", "method": "POST", "path": "/Booking/Cancel/1602", "form": {"CandidateNumber": "NW00000001", "Reason": "characterization"}}`

```
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000001' -> '/app/my-bookings'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Noah Nakamura', 'Candidate no.': 'NW00000001', 'Email': 'noah.nakamura1@example.net', 'Default accommodatio...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.steps[0].sessions_after[0].SeatsAvailable: 1 -> 0
$.after.audit: length 1 -> 0
$.after.bookings[22].CancelReason: 'characterization' -> None
$.after.bookings[22].CancelledAt: '<now>' -> None
$.after.bookings[22].LastModified: '<now>' -> '2026-11-09T01:12:00'
$.after.bookings[22].Status: 'CANCELLED' -> 'BOOKED'
$.after.sessions[0].BookedOrAttended: 21 -> 22
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 1 -> 0
```

### CN-06-api-cancel-on-full-session-no-promotion

- allowed by: [ADR 0005](../../docs/adr/0005-waitlist-auto-offer.md) decisions 1, 3, 7 (the freed seat is offered to waitlist position 1: `WAITLIST_OFFER` audit row; the offered booking stays `WAITLISTED` in legacy, its row is re-projected so `LastModified` is stamped) - supersedes ODD-09 for BOOKED cancels
- requests:
  - api_cancel: `{"channel": "api", "method": "DELETE", "path": "/api/v1/bookings/1601"}`

```
$.after.audit: length 1 -> 2
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'Cancelled by partner' -> 'outbox seq=26 event=52d41fd7-9021-4b3d-a2a7-d347e9e5ac37 BookingCancelled reason=Cancelled by partner'
$.after.bookings[23].LastModified: '2026-11-09T10:43:00' -> '<now>'
```

### CN-07-portal-cancel-twice

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 154, "CandidateNumber": "NW00000917", "AccommodationCode": ""}}`
  - portal_cancel: `{"channel": "portal", "method": "POST", "path": "/Booking/Cancel/<booking:1>", "form": {"CandidateNumber": "NW00000917", "Reason": "characterization"}}`
  - portal_cancel: `{"channel": "portal", "method": "POST", "path": "/Booking/Cancel/<booking:1>", "form": {"CandidateNumber": "NW00000917", "Reason": "characterization"}}`

```
replay ERROR: RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=154', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

### CN-08-api-cancel-twice

- allowed by: [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`); [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone
- requests:
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 154, "candidateNumber": "NW00000918"}}`
  - api_cancel: `{"channel": "api", "method": "DELETE", "path": "/api/v1/bookings/<booking:1>"}`
  - api_cancel: `{"channel": "api", "method": "DELETE", "path": "/api/v1/bookings/<booking:1>"}`

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER' -> 'outbox seq=30 event=6e47cd83-877d-4552-9103-69e287838796 BookingCreated channel=PARTNER'
$.before.audit[1].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[1].Detail: 'Cancelled by partner' -> 'outbox seq=31 event=11c7382b-1e89-48ec-8524-a19baac7c359 BookingCancelled reason=Cancelled by partner'
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 10 -> 11
$.steps[0].sessions_after[0].SeatsAvailable: 10 -> 11
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER' -> 'outbox seq=30 event=6e47cd83-877d-4552-9103-69e287838796 BookingCreated channel=PARTNER'
$.after.audit[1].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[1].Detail: 'Cancelled by partner' -> 'outbox seq=31 event=11c7382b-1e89-48ec-8524-a19baac7c359 BookingCancelled reason=Cancelled by partner'
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[0].SeatsAvailable: 10 -> 11
```

### CP-01-portal-capacity-increase

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_capacity: `{"channel": "portal", "method": "POST", "path": "/Admin/Sessions/Capacity/154", "form": {"NewCapacity": 25}}`

```
$.steps[0].response.path: '/Admin/Sessions/Capacity/154' -> '/app/admin/sessions/154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[0].sessions_after[0].Capacity: 25 -> 20
$.steps[0].sessions_after[0].SeatsAvailable: 16 -> 11
$.after.audit: length 1 -> 0
$.after.sessions[0].Capacity: 25 -> 20
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 16 -> 11
```

### CP-02-portal-capacity-decrease-above-active

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_capacity: `{"channel": "portal", "method": "POST", "path": "/Admin/Sessions/Capacity/154", "form": {"NewCapacity": 12}}`

```
$.steps[0].response.path: '/Admin/Sessions/Capacity/154' -> '/app/admin/sessions/154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[0].sessions_after[0].Capacity: 12 -> 20
$.steps[0].sessions_after[0].SeatsAvailable: 3 -> 11
$.after.audit: length 1 -> 0
$.after.sessions[0].Capacity: 12 -> 20
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 3 -> 11
```

### CP-03-portal-capacity-decrease-below-active

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_capacity: `{"channel": "portal", "method": "POST", "path": "/Admin/Sessions/Capacity/154", "form": {"NewCapacity": 6}}`

```
$.steps[0].response.path: '/Admin/Sessions/Capacity/154' -> '/app/admin/sessions/154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[0].sessions_after[0].Capacity: 6 -> 20
$.steps[0].sessions_after[0].SeatsAvailable: 0 -> 11
$.after.audit: length 1 -> 0
$.after.bookings[7].LastModified: '<now>' -> '2026-11-09T10:59:00'
$.after.bookings[7].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[8].LastModified: '<now>' -> '2026-11-09T20:30:00'
$.after.bookings[8].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[9].LastModified: '<now>' -> '2026-11-10T04:21:00'
$.after.bookings[9].Status: 'WAITLISTED' -> 'BOOKED'
$.after.sessions[0].BookedOrAttended: 6 -> 9
$.after.sessions[0].Capacity: 6 -> 20
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 0 -> 11
$.after.sessions[0].Waitlisted: 3 -> 0
$.after.waitlist: length 3 -> 0
```

### CP-04-portal-capacity-raise-then-lower

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_capacity: `{"channel": "portal", "method": "POST", "path": "/Admin/Sessions/Capacity/154", "form": {"NewCapacity": 24}}`
  - portal_capacity: `{"channel": "portal", "method": "POST", "path": "/Admin/Sessions/Capacity/154", "form": {"NewCapacity": 10}}`
  - portal_capacity: `{"channel": "portal", "method": "POST", "path": "/Admin/Sessions/Capacity/154", "form": {"NewCapacity": 7}}`

```
$.steps[0].response.path: '/Admin/Sessions/Capacity/154' -> '/app/admin/sessions/154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[0].sessions_after[0].Capacity: 24 -> 20
$.steps[0].sessions_after[0].SeatsAvailable: 15 -> 11
$.steps[1].response.path: '/Admin/Sessions/Capacity/154' -> '/app/admin/sessions/154'
$.steps[1].response.rendered.alerts: length 1 -> 0
$.steps[1].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[1].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[1].sessions_after[0].Capacity: 10 -> 20
$.steps[1].sessions_after[0].SeatsAvailable: 1 -> 11
$.steps[2].response.path: '/Admin/Sessions/Capacity/154' -> '/app/admin/sessions/154'
$.steps[2].response.rendered.alerts: length 1 -> 0
$.steps[2].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[2].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[2].sessions_after[0].Capacity: 7 -> 20
$.steps[2].sessions_after[0].SeatsAvailable: 0 -> 11
$.after.audit: length 3 -> 0
$.after.bookings[8].LastModified: '<now>' -> '2026-11-09T20:30:00'
$.after.bookings[8].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[9].LastModified: '<now>' -> '2026-11-10T04:21:00'
$.after.bookings[9].Status: 'WAITLISTED' -> 'BOOKED'
$.after.sessions[0].BookedOrAttended: 7 -> 9
$.after.sessions[0].Capacity: 7 -> 20
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 0 -> 11
$.after.sessions[0].Waitlisted: 2 -> 0
$.after.waitlist: length 2 -> 0
```

### CP-05-portal-capacity-below-accommodation

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_capacity: `{"channel": "portal", "method": "POST", "path": "/Admin/Sessions/Capacity/154", "form": {"NewCapacity": 1}}`

```
$.steps[0].response.path: '/Admin/Sessions/Capacity/154' -> '/app/admin/sessions/154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
```

### CP-06-db-capacity-below-accommodation-error

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PROC-DROPPED (proc dropped: SQL error 2812 instead of 50000)
- requests:
  - db_proc: `{"channel": "db", "sql": "EXEC dbo.usp_ChangeCapacity @SessionId = 154, @NewCapacity = 1, @Actor = 'characterization';"}`

```
$.steps[0].response.errors[0].message: 'Invalid capacity 1 for session 154.' -> "Could not find stored procedure 'dbo.usp_ChangeCapacity'."
$.steps[0].response.errors[0].number: 50000 -> 2812
$.steps[0].response.errors[0].state: 5 -> 62
```

### CP-07-portal-capacity-decrease-demotes-newest-mixed-channels

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 154, "CandidateNumber": "NW00000930", "AccommodationCode": ""}}`
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 154, "candidateNumber": "NW00000931", "externalRef": "PX-4007"}}`
  - portal_capacity: `{"channel": "portal", "method": "POST", "path": "/Admin/Sessions/Capacity/154", "form": {"NewCapacity": 10}}`

```
$.before.audit: length 2 -> 1
$.before.audit[0].Actor: 'ad_app' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=154 status=BOOKED' -> 'outbox seq=56 event=a416b6a1-a740-496f-bf2b-fd96a071c469 BookingCreated channel=PARTNER'
$.before.bookings: length 12 -> 11
$.before.bookings[10].CandidateNumber: 'NW00000930' -> 'NW00000931'
$.before.bookings[10].Channel: 'PORTAL' -> 'PARTNER'
$.before.bookings[10].ExternalRef: None -> 'PX-4007'
$.before.sessions[0].BookedOrAttended: 11 -> 10
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 8 -> 10
$.steps[0].response.path: '/Admin/Sessions/Capacity/154' -> '/app/admin/sessions/154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[0].sessions_after[0].Capacity: 10 -> 20
$.steps[0].sessions_after[0].SeatsAvailable: 0 -> 10
$.after.audit: length 3 -> 1
$.after.audit[0].Actor: 'ad_app' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED' -> 'outbox seq=56 event=a416b6a1-a740-496f-bf2b-fd96a071c469 BookingCreated channel=PARTNER'
$.after.bookings: length 12 -> 11
$.after.bookings[10].CandidateNumber: 'NW00000930' -> 'NW00000931'
$.after.bookings[10].Channel: 'PORTAL' -> 'PARTNER'
$.after.bookings[10].ExternalRef: None -> 'PX-4007'
$.after.sessions[0].Capacity: 10 -> 20
$.after.sessions[0].SeatsAvailable: 0 -> 10
$.after.sessions[0].Waitlisted: 1 -> 0
$.after.waitlist: length 1 -> 0
```

### CP-08-portal-capacity-equal-to-accommodation

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_capacity: `{"channel": "portal", "method": "POST", "path": "/Admin/Sessions/Capacity/154", "form": {"NewCapacity": 2}}`

```
$.steps[0].response.path: '/Admin/Sessions/Capacity/154' -> '/app/admin/sessions/154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[0].sessions_after[0].Capacity: 2 -> 20
$.steps[0].sessions_after[0].SeatsAvailable: 0 -> 11
$.after.audit: length 1 -> 0
$.after.bookings[2].LastModified: '<now>' -> '2026-11-07T08:04:00'
$.after.bookings[2].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[3].LastModified: '<now>' -> '2026-11-07T17:35:00'
$.after.bookings[3].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[4].LastModified: '<now>' -> '2026-11-08T03:06:00'
$.after.bookings[4].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[6].LastModified: '<now>' -> '2026-11-09T01:28:00'
$.after.bookings[6].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[7].LastModified: '<now>' -> '2026-11-09T10:59:00'
$.after.bookings[7].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[8].LastModified: '<now>' -> '2026-11-09T20:30:00'
$.after.bookings[8].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[9].LastModified: '<now>' -> '2026-11-10T04:21:00'
$.after.bookings[9].Status: 'WAITLISTED' -> 'BOOKED'
$.after.sessions[0].BookedOrAttended: 2 -> 9
$.after.sessions[0].Capacity: 2 -> 20
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 0 -> 11
$.after.sessions[0].Waitlisted: 7 -> 0
$.after.waitlist: length 7 -> 0
```

### CP-09-db-capacity-decrease-below-active

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PROC-DROPPED (proc dropped: SQL error 2812 instead of 50000)
- requests:
  - db_proc: `{"channel": "db", "sql": "EXEC dbo.usp_ChangeCapacity @SessionId = 154, @NewCapacity = 6, @Actor = 'characterization';"}`

```
$.steps[0].response.errors: length 0 -> 1
$.steps[0].sessions_after[0].Capacity: 6 -> 20
$.steps[0].sessions_after[0].SeatsAvailable: 0 -> 11
$.after.audit: length 1 -> 0
$.after.bookings[7].LastModified: '<now>' -> '2026-11-09T10:59:00'
$.after.bookings[7].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[8].LastModified: '<now>' -> '2026-11-09T20:30:00'
$.after.bookings[8].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[9].LastModified: '<now>' -> '2026-11-10T04:21:00'
$.after.bookings[9].Status: 'WAITLISTED' -> 'BOOKED'
$.after.sessions[0].BookedOrAttended: 6 -> 9
$.after.sessions[0].Capacity: 6 -> 20
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 0 -> 11
$.after.sessions[0].Waitlisted: 3 -> 0
$.after.waitlist: length 3 -> 0
```

### MX-01-mixed-traffic-then-capacity-sync

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-MIXED (portal bookings through :8000 complete via `/app` = `POST /api/v2/bookings`), [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (one seat per booking, so fewer bookings are waitlisted), [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §8 (capacity-sync `SKIPPED`)
- requests:
  - seat-drift before traffic: `{"channel": "db", "check": "db/checks/seat-drift.sql"}`
  - mixed_traffic: `{"channel": "mixed", "workload": "scripts/mixed-traffic.ps1", "PORTAL_BOOKINGS": 20, "PARTNER_BOOKINGS": 20, "CANCELS": 3, "SESSIONS": 4, "FIRST_CANDIDATE": 901, "SEED_AS_OF": "2026-11-10"}`
  - seat-drift after traffic: `{"channel": "db", "check": "db/checks/seat-drift.sql"}`
  - capacity_sync: `{"channel": "sync", "method": "POST", "path": "/sync/run"}`
  - seat-drift after capacity-sync: `{"channel": "db", "check": "db/checks/seat-drift.sql"}`

```
$.steps[1].response.calls: length 46 -> 43
$.steps[1].response.calls[0].alerts: length 1 -> 0
$.steps[1].response.calls[1].body.bookingId: '<booking:2>' -> '<booking:1>'
$.steps[1].response.calls[2].alerts: length 1 -> 0
$.steps[1].response.calls[3].body.bookingId: '<booking:4>' -> '<booking:2>'
$.steps[1].response.calls[4].alerts: length 1 -> 0
$.steps[1].response.calls[5].body.bookingId: '<booking:6>' -> '<booking:3>'
$.steps[1].response.calls[6].alerts: length 1 -> 0
$.steps[1].response.calls[7].body.bookingId: '<booking:8>' -> '<booking:4>'
$.steps[1].response.calls[8].alerts: length 1 -> 0
$.steps[1].response.calls[9].body.bookingId: '<booking:10>' -> '<booking:5>'
$.steps[1].response.calls[10].alerts: length 1 -> 0
$.steps[1].response.calls[11].body.bookingId: '<booking:12>' -> '<booking:6>'
$.steps[1].response.calls[12].alerts: length 1 -> 0
$.steps[1].response.calls[13].body.bookingId: '<booking:14>' -> '<booking:7>'
$.steps[1].response.calls[14].alerts: length 1 -> 0
$.steps[1].response.calls[15].body.bookingId: '<booking:16>' -> '<booking:8>'
$.steps[1].response.calls[15].body.status: 'WAITLISTED' -> 'BOOKED'
$.steps[1].response.calls[16].alerts: length 1 -> 0
$.steps[1].response.calls[17].body.bookingId: '<booking:18>' -> '<booking:9>'
$.steps[1].response.calls[18].alerts: length 1 -> 0
$.steps[1].response.calls[19].body.bookingId: '<booking:20>' -> '<booking:10>'
$.steps[1].response.calls[19].body.status: 'WAITLISTED' -> 'BOOKED'
$.steps[1].response.calls[20].alerts: length 1 -> 0
$.steps[1].response.calls[21].body.bookingId: '<booking:22>' -> '<booking:11>'
$.steps[1].response.calls[22].alerts: length 1 -> 0
$.steps[1].response.calls[23].body.bookingId: '<booking:24>' -> '<booking:12>'
$.steps[1].response.calls[24].alerts: length 1 -> 0
$.steps[1].response.calls[25].body.bookingId: '<booking:26>' -> '<booking:13>'
$.steps[1].response.calls[25].body.status: 'WAITLISTED' -> 'BOOKED'
$.steps[1].response.calls[26].alerts: length 1 -> 0
$.steps[1].response.calls[27].body.bookingId: '<booking:28>' -> '<booking:14>'
$.steps[1].response.calls[28].alerts: length 1 -> 0
$.steps[1].response.calls[29].body.bookingId: '<booking:30>' -> '<booking:15>'
$.steps[1].response.calls[29].body.status: 'WAITLISTED' -> 'BOOKED'
$.steps[1].response.calls[30].alerts: length 1 -> 0
$.steps[1].response.calls[31].body.bookingId: '<booking:32>' -> '<booking:16>'
$.steps[1].response.calls[32].alerts: length 1 -> 0
$.steps[1].response.calls[33].body.bookingId: '<booking:34>' -> '<booking:17>'
$.steps[1].response.calls[33].body.status: 'WAITLISTED' -> 'BOOKED'
$.steps[1].response.calls[34].alerts: length 1 -> 0
$.steps[1].response.calls[35].body.bookingId: '<booking:36>' -> '<booking:18>'
$.steps[1].response.calls[36].alerts: length 1 -> 0
$.steps[1].response.calls[37].body.bookingId: '<booking:38>' -> '<booking:19>'
$.steps[1].response.calls[37].body.status: 'WAITLISTED' -> 'BOOKED'
$.steps[1].response.calls[38].alerts: length 1 -> 0
$.steps[1].response.calls[39].body.bookingId: '<booking:40>' -> '<booking:20>'
$.steps[1].response.calls[40].alerts: missing in replay (expected ['Booking <booking:1> cancelled.'])
$.steps[1].response.calls[40].op: 'portal_cancel' -> 'api_cancel'
$.steps[1].response.calls[41].alerts: missing in replay (expected ['Booking <booking:3> cancelled.'])
$.after.audit: length 47 -> 25
$.after.audit[0].Actor: 'ad_app' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=152 status=BOOKED' -> 'outbox seq=89 event=92808e5a-cdda-47c1-bec4-a9196459ed54 BookingCreated channel=PARTNER'
$.after.audit[1].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[1].Detail: 'session=154 status=BOOKED channel=PARTNER ref=MT-0001' -> 'outbox seq=90 event=1923b570-e7a5-4228-aa14-ad51eb4e1d22 BookingCreated channel=PARTNER'
$.after.audit[2].Actor: 'ad_app' -> 'scheduling-service'
$.after.audit[2].Detail: 'session=155 status=BOOKED' -> 'outbox seq=91 event=305d4479-942e-45b6-9e9e-008f31b41677 BookingCreated channel=PARTNER'
$.after.audit[3].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[3].Detail: 'session=157 status=BOOKED channel=PARTNER ref=MT-0002' -> 'outbox seq=92 event=9681bf8e-0d96-4520-9d6d-ab6af457786b BookingCreated channel=PARTNER'
$.after.audit[4].Actor: 'ad_app' -> 'scheduling-service'
$.after.audit[4].Detail: 'session=152 status=BOOKED' -> 'outbox seq=93 event=577bcfed-2c22-4081-b91d-fb287e2cc528 BookingCreated channel=PARTNER'
$.after.audit[5].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[5].Detail: 'session=154 status=BOOKED channel=PARTNER ref=MT-0003' -> 'outbox seq=94 event=d67902bf-c5f5-404d-9347-37dd2e4e8390 BookingCreated channel=PARTNER'
$.after.audit[6].Actor: 'ad_app' -> 'scheduling-service'
$.after.audit[6].Detail: 'session=155 status=BOOKED' -> 'outbox seq=95 event=40c6cba5-5adb-4d16-bff0-1d520f762b05 BookingCreated channel=PARTNER'
$.after.audit[7].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[7].Detail: 'session=157 status=BOOKED channel=PARTNER ref=MT-0004' -> 'outbox seq=96 event=fafa03cf-68f0-4247-971a-15a108a026bd BookingCreated channel=PARTNER'
$.after.audit[8].Actor: 'ad_app' -> 'scheduling-service'
$.after.audit[8].Detail: 'session=152 status=BOOKED' -> 'outbox seq=97 event=b6e14c1e-ab89-4c00-85e7-bc4e624f3b33 BookingCreated channel=PARTNER'
$.after.audit[9].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[9].Detail: 'session=154 status=BOOKED channel=PARTNER ref=MT-0005' -> 'outbox seq=98 event=def73eeb-86eb-4e18-b1b7-ada958ec2b41 BookingCreated channel=PARTNER'
$.after.audit[10].Actor: 'ad_app' -> 'scheduling-service'
$.after.audit[10].Detail: 'session=155 status=BOOKED' -> 'outbox seq=99 event=dd0d9577-2186-4b52-a843-00e60104bc33 BookingCreated channel=PARTNER'
$.after.audit[11].Action: 'BOOK' -> 'WAITLIST'
$.after.audit[11].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[11].Detail: 'session=157 status=BOOKED channel=PARTNER ref=MT-0006' -> 'outbox seq=100 event=8aba1d48-90c8-4f11-91d6-40f43b44c842 BookingWaitlisted channel=PARTNER'
$.after.audit[12].Actor: 'ad_app' -> 'scheduling-service'
$.after.audit[12].Detail: 'session=152 status=BOOKED' -> 'outbox seq=101 event=9ac74ff2-1a4e-43ac-aad2-9b479b29a3ee BookingCreated channel=PARTNER'
$.after.audit[13].Action: 'BOOK' -> 'WAITLIST'
$.after.audit[13].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[13].Detail: 'session=154 status=BOOKED channel=PARTNER ref=MT-0007' -> 'outbox seq=102 event=57abbba9-3e52-4a8b-aa93-72d68c3d57f4 BookingWaitlisted channel=PARTNER'
$.after.audit[14].Actor: 'ad_app' -> 'scheduling-service'
$.after.audit[14].Detail: 'session=155 status=BOOKED' -> 'outbox seq=103 event=90bc8008-a9e8-40fa-b265-42670e42750b BookingCreated channel=PARTNER'
$.after.audit[15].Action: 'BOOK' -> 'WAITLIST'
$.after.audit[15].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[15].Detail: 'session=157 status=WAITLISTED channel=PARTNER ref=MT-0008' -> 'outbox seq=104 event=03f20f7a-c111-40d8-b23c-f3df18defd71 BookingWaitlisted channel=PARTNER'
$.after.audit[16].Actor: 'ad_app' -> 'scheduling-service'
$.after.audit[16].Detail: 'session=152 status=BOOKED' -> 'outbox seq=105 event=7a039dab-820a-4669-8bb0-059334b1af93 BookingCreated channel=PARTNER'
$.after.audit[17].Action: 'BOOK' -> 'WAITLIST'
$.after.audit[17].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[17].Detail: 'session=154 status=BOOKED channel=PARTNER ref=MT-0009' -> 'outbox seq=106 event=28735b60-6514-41b3-8566-91f59aff273c BookingWaitlisted channel=PARTNER'
$.after.audit[18].Actor: 'ad_app' -> 'scheduling-service'
$.after.audit[18].Detail: 'session=155 status=BOOKED' -> 'outbox seq=107 event=606417df-83f5-4023-94fb-9df8f557b0a4 BookingCreated channel=PARTNER'
$.after.audit[19].Action: 'BOOK' -> 'WAITLIST'
$.after.audit[19].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[19].Detail: 'session=157 status=WAITLISTED channel=PARTNER ref=MT-0010' -> 'outbox seq=108 event=d1b36a43-a34a-4325-be2c-c5624d547578 BookingWaitlisted channel=PARTNER'
$.after.audit[20].Action: 'BOOK' -> 'CANCEL'
$.after.audit[20].Actor: 'ad_app' -> 'scheduling-service'
$.after.audit[20].Detail: 'session=152 status=BOOKED' -> 'outbox seq=109 event=7ae0df92-4ed3-4647-90c8-186d194b6879 BookingCancelled reason=mixed-traffic'
$.after.audit[20].EntityId: '<booking:21>' -> '<booking:1>'
```

### MX-02-capacity-sync-on-fresh-seed

- allowed by: [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §8/§9 (capacity-sync disabled, `SKIPPED`; reconciliation replaces it) and [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-03 (one seat formula, so 0 drift on the fresh seed)
- requests:
  - seat-drift before capacity-sync: `{"channel": "db", "check": "db/checks/seat-drift.sql"}`
  - capacity_sync: `{"channel": "sync", "method": "POST", "path": "/sync/run"}`
  - seat-drift after capacity-sync: `{"channel": "db", "check": "db/checks/seat-drift.sql"}`

```
$.steps[1].response.body.message: None -> "capacity.sync.enabled=false: SeatsAvailable is written by scheduling-service's relay; use GET /api/v2/internal/recon...
$.steps[1].response.body.sessionsChecked: 285 -> 0
$.steps[1].response.body.sessionsUpdated: 112 -> 0
$.steps[1].response.body.status: 'COMPLETED' -> 'SKIPPED'
$.steps[1].sessions_after[0].SeatsAvailable: 14 -> 16
$.steps[1].sessions_after[1].SeatsAvailable: 9 -> 11
$.steps[2].response.drifting_sessions: 112 -> 0
$.steps[2].response.rows: length 112 -> 0
$.steps[2].response.total_drift: 177 -> 0
$.steps[2].sessions_after[0].SeatsAvailable: 14 -> 16
$.steps[2].sessions_after[1].SeatsAvailable: 9 -> 11
$.after.audit[0].Detail: 'trigger=MANUAL status=COMPLETED checked=285 updated=112' -> 'trigger=MANUAL status=SKIPPED checked=0 updated=0 reason=capacity.sync.enabled=false'
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatDrift: -2 -> 0
$.after.sessions[0].SeatsAvailable: 14 -> 16
$.after.sessions[1].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[1].SeatDrift: -2 -> 0
$.after.sessions[1].SeatsAvailable: 9 -> 11
```

### MX-03-capacity-sync-with-accommodation-bookings

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ); [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §8 (capacity-sync `SKIPPED`) and [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-03/ODD-13 (0 drift before and after)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 163, "CandidateNumber": "NW00000941", "AccommodationCode": "ET25"}}`
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 163, "candidateNumber": "NW00000942"}}`
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 163, "CandidateNumber": "NW00000944", "AccommodationCode": ""}}`
  - seat-drift before capacity-sync: `{"channel": "db", "check": "db/checks/seat-drift.sql"}`
  - capacity_sync: `{"channel": "sync", "method": "POST", "path": "/sync/run"}`
  - seat-drift after capacity-sync: `{"channel": "db", "check": "db/checks/seat-drift.sql"}`

```
$.before.audit: length 3 -> 1
$.before.audit[0].Actor: 'ad_app' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=163 status=BOOKED' -> 'outbox seq=115 event=0566e0bb-ae08-4508-837e-cb82a7a329be BookingCreated channel=PARTNER'
$.before.bookings: length 6 -> 4
$.before.bookings[3].AccommodationCode: 'ET25' -> None
$.before.bookings[3].CandidateNumber: 'NW00000941' -> 'NW00000942'
$.before.bookings[3].Channel: 'PORTAL' -> 'PARTNER'
$.before.sessions[0].BookedOrAttended: 6 -> 4
$.before.sessions[0].ExtraTimeBooked: 1 -> 0
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 1 -> 4
$.steps[0].response.drifting_sessions: 1 -> 0
$.steps[0].response.rows: length 1 -> 0
$.steps[0].response.total_drift: 1 -> 0
$.steps[0].sessions_after[0].SeatsAvailable: 1 -> 4
$.steps[1].response.body.message: None -> "capacity.sync.enabled=false: SeatsAvailable is written by scheduling-service's relay; use GET /api/v2/internal/recon...
$.steps[1].response.body.sessionsChecked: 285 -> 0
$.steps[1].response.body.sessionsUpdated: 112 -> 0
$.steps[1].response.body.status: 'COMPLETED' -> 'SKIPPED'
$.steps[1].sessions_after[0].SeatsAvailable: 2 -> 4
$.steps[2].response.drifting_sessions: 111 -> 0
$.steps[2].response.rows: length 111 -> 0
$.steps[2].response.total_drift: 176 -> 0
$.steps[2].sessions_after[0].SeatsAvailable: 2 -> 4
$.after.audit: length 4 -> 2
$.after.audit[0].Actor: 'ad_app' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=163 status=BOOKED' -> 'outbox seq=115 event=0566e0bb-ae08-4508-837e-cb82a7a329be BookingCreated channel=PARTNER'
$.after.audit[1].Action: 'BOOK' -> 'CAPACITY_SYNC'
$.after.audit[1].Actor: 'booking-api' -> 'capacity-sync'
$.after.audit[1].Detail: 'session=163 status=BOOKED channel=PARTNER' -> 'trigger=MANUAL status=SKIPPED checked=0 updated=0 reason=capacity.sync.enabled=false'
$.after.audit[1].EntityId: '<booking:2>' -> 0
$.after.audit[1].EntityName: 'Booking' -> 'ExamSession'
$.after.bookings: length 6 -> 4
$.after.bookings[3].AccommodationCode: 'ET25' -> None
$.after.bookings[3].CandidateNumber: 'NW00000941' -> 'NW00000942'
$.after.bookings[3].Channel: 'PORTAL' -> 'PARTNER'
$.after.sessions[0].BookedOrAttended: 6 -> 4
$.after.sessions[0].ExtraTimeBooked: 1 -> 0
$.after.sessions[0].SeatsAvailable: 2 -> 4
```

### RS-01-portal-reschedule-same-exam

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 154, "CandidateNumber": "NW00000919", "AccommodationCode": ""}}`
  - portal_reschedule: `{"channel": "portal", "method": "POST", "path": "/Booking/Reschedule/<booking:1>", "form": {"CandidateNumber": "NW00000919", "NewSessionId": 163}}`

```
replay ERROR: RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=154', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

### RS-02-api-reschedule-same-exam

- allowed by: [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`); [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone; [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-05 (reschedule = cancel + book; one `BookingRescheduled` event instead of CANCEL + BOOK rows)
- requests:
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 154, "candidateNumber": "NW00000920", "externalRef": "PX-3001"}}`
  - api_reschedule: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings/<booking:1>/reschedule", "json": {"newSessionId": 163}}`

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER ref=PX-3001' -> 'outbox seq=34 event=73580d31-80c0-4006-9063-f89cee06fe26 BookingCreated channel=PARTNER'
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 9 -> 10
$.steps[0].sessions_after[0].SeatsAvailable: 10 -> 11
$.steps[0].sessions_after[1].SeatsAvailable: 3 -> 4
$.after.audit: length 3 -> 2
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER ref=PX-3001' -> 'outbox seq=34 event=73580d31-80c0-4006-9063-f89cee06fe26 BookingCreated channel=PARTNER'
$.after.audit[1].Action: 'CANCEL' -> 'RESCHEDULE'
$.after.audit[1].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[1].Detail: 'Rescheduled' -> 'outbox seq=35 event=7222ad78-cec4-4ba4-ac33-daa778ea74aa BookingRescheduled from=<booking:1> to=<booking:2>'
$.after.audit[1].EntityId: '<booking:1>' -> '<booking:2>'
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[0].SeatsAvailable: 10 -> 11
$.after.sessions[1].SeatDrift: -1 -> 0
$.after.sessions[1].SeatsAvailable: 3 -> 4
```

### RS-03-portal-reschedule-different-exam

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 154, "CandidateNumber": "NW00000921", "AccommodationCode": ""}}`
  - portal_reschedule: `{"channel": "portal", "method": "POST", "path": "/Booking/Reschedule/<booking:1>", "form": {"CandidateNumber": "NW00000921", "NewSessionId": 155}}`

```
replay ERROR: RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=154', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

### RS-04-api-reschedule-different-exam

- allowed by: [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`); [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone
- requests:
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 154, "candidateNumber": "NW00000922"}}`
  - api_reschedule: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings/<booking:1>/reschedule", "json": {"newSessionId": 155}}`

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER' -> 'outbox seq=38 event=55062c25-aa86-49e8-938b-4c9487f29164 BookingCreated channel=PARTNER'
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 9 -> 10
$.steps[0].sessions_after[0].SeatsAvailable: 9 -> 10
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER' -> 'outbox seq=38 event=55062c25-aa86-49e8-938b-4c9487f29164 BookingCreated channel=PARTNER'
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[0].SeatsAvailable: 9 -> 10
```

### RS-05-portal-reschedule-same-session

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 154, "CandidateNumber": "NW00000924", "AccommodationCode": ""}}`
  - portal_reschedule: `{"channel": "portal", "method": "POST", "path": "/Booking/Reschedule/<booking:1>", "form": {"CandidateNumber": "NW00000924", "NewSessionId": 154}}`

```
replay ERROR: RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=154', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

### RS-06-api-reschedule-same-session

- allowed by: [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`); [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone
- requests:
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 154, "candidateNumber": "NW00000925"}}`
  - api_reschedule: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings/<booking:1>/reschedule", "json": {"newSessionId": 154}}`

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER' -> 'outbox seq=41 event=6fa0581b-4c08-43d9-ad7a-0162090eda7a BookingCreated channel=PARTNER'
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 9 -> 10
$.steps[0].sessions_after[0].SeatsAvailable: 9 -> 10
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER' -> 'outbox seq=41 event=6fa0581b-4c08-43d9-ad7a-0162090eda7a BookingCreated channel=PARTNER'
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[0].SeatsAvailable: 9 -> 10
```

### RS-07-db-reschedule-different-exam-error

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ); [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PROC-DROPPED (proc dropped: SQL error 2812 instead of 50000)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 154, "CandidateNumber": "NW00000926", "AccommodationCode": ""}}`
  - db_proc: `{"channel": "db", "sql": "DECLARE @nb INT, @s VARCHAR(20); EXEC dbo.usp_RescheduleBooking @BookingId = <booking:1>, @NewSessionId = 155, @NewBookingId = @nb OUTPUT, @ResultStatus = @s OUTPUT; SELECT ISNULL(@s, 'NULL');"}`

```
replay ERROR: RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=154', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

### RS-08-portal-reschedule-partner-booking

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 154, "candidateNumber": "NW00000927", "externalRef": "PX-3008"}}`
  - portal_reschedule: `{"channel": "portal", "method": "POST", "path": "/Booking/Reschedule/<booking:1>", "form": {"CandidateNumber": "NW00000927", "NewSessionId": 163}}`

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER ref=PX-3008' -> 'outbox seq=44 event=a7409057-c5ab-49f5-8f3e-e8c395cfc4b3 BookingCreated channel=PARTNER'
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 9 -> 10
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000927' -> '/app/reschedule/<booking:1>'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Ethan Kearney', 'Candidate no.': 'NW00000927', 'Email': 'ethan.kearney927@example.com', 'Default accommodat...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.steps[0].sessions_after[1].SeatsAvailable: 4 -> 5
$.after.audit: length 3 -> 1
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER ref=PX-3008' -> 'outbox seq=44 event=a7409057-c5ab-49f5-8f3e-e8c395cfc4b3 BookingCreated channel=PARTNER'
$.after.bookings: length 15 -> 14
$.after.bookings[10].CancelReason: 'Rescheduled' -> None
$.after.bookings[10].CancelledAt: '<now>' -> None
$.after.bookings[10].Status: 'CANCELLED' -> 'BOOKED'
$.after.sessions[0].BookedOrAttended: 9 -> 10
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[1].BookedOrAttended: 4 -> 3
$.after.sessions[1].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[1].SeatsAvailable: 4 -> 5
```

### RS-09-api-reschedule-to-full-session

- allowed by: [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) §4/§5 (the relay writes one `AuditLog` row per outbox event: actor/detail from the event, actions `WAITLIST`/`RESCHEDULE` for `BookingWaitlisted`/`BookingRescheduled`); [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-01/ODD-13 (every booking takes exactly one seat, seats derived) with [ADR 0003](../../docs/adr/0003-single-writer-outbox-relay.md) consequence `SeatsAvailable = Capacity - (BOOKED + ATTENDED)`: the legacy partner double decrement and its drift are gone; [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-05 (reschedule = cancel + book; one `BookingRescheduled` event instead of CANCEL + BOOK rows)
- requests:
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 158, "candidateNumber": "NW00000928", "externalRef": "PX-3009"}}`
  - api_reschedule: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings/<booking:1>/reschedule", "json": {"newSessionId": 153}}`

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=158 status=BOOKED channel=PARTNER ref=PX-3009' -> 'outbox seq=46 event=b617b088-045d-4628-8026-456cd44de48b BookingCreated channel=PARTNER'
$.before.sessions[1].SeatDrift: -1 -> 0
$.before.sessions[1].SeatsAvailable: 5 -> 6
$.steps[0].sessions_after[1].SeatsAvailable: 6 -> 7
$.after.audit: length 3 -> 2
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=158 status=BOOKED channel=PARTNER ref=PX-3009' -> 'outbox seq=46 event=b617b088-045d-4628-8026-456cd44de48b BookingCreated channel=PARTNER'
$.after.audit[1].Action: 'CANCEL' -> 'RESCHEDULE'
$.after.audit[1].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[1].Detail: 'Rescheduled' -> 'outbox seq=47 event=42211f1e-5454-4daf-86f6-0d0ee5838be8 BookingRescheduled from=<booking:1> to=<booking:2>'
$.after.audit[1].EntityId: '<booking:1>' -> '<booking:2>'
$.after.sessions[1].SeatDrift: -1 -> 0
$.after.sessions[1].SeatsAvailable: 6 -> 7
```

### RS-10-portal-reschedule-to-full-session

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 158, "CandidateNumber": "NW00000929", "AccommodationCode": ""}}`
  - portal_reschedule: `{"channel": "portal", "method": "POST", "path": "/Booking/Reschedule/<booking:1>", "form": {"CandidateNumber": "NW00000929", "NewSessionId": 153}}`

```
replay ERROR: RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=158', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

### RT-14-portal-book-retake-between-boundaries

- allowed by: [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md) M5-PORTAL-REDIRECT (the retired portal page answered 303/302 to `/app`; the step writes nothing, later snapshots differ)
- requests:
  - portal_book: `{"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": {"SessionId": 216, "CandidateNumber": "NW00000113", "AccommodationCode": ""}}`

```
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000113' -> '/app/book?sessionId=216'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Adam Gallagher', 'Candidate no.': 'NW00000113', 'Email': 'adam.gallagher113@example.org', 'Default accommod...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.steps[0].sessions_after[0].SeatsAvailable: 8 -> 9
$.after.audit: length 1 -> 0
$.after.bookings: length 13 -> 12
$.after.bookings[10].BookedAt: '<now>' -> '2026-11-10T16:07:00'
$.after.bookings[10].BookingId: '<booking:1>' -> 3497
$.after.bookings[10].Channel: 'PORTAL' -> 'PARTNER'
$.after.bookings[10].ExternalRef: None -> 'PTR-R221113'
$.after.bookings[10].LastModified: '<now>' -> '2026-11-10T16:07:00'
$.after.bookings[10].SessionId: 216 -> 221
$.after.bookings[11].BookedAt: '2026-11-10T16:07:00' -> '2026-11-06T23:54:00'
$.after.bookings[11].BookingId: 3497 -> 3178
$.after.bookings[11].CancelReason: None -> 'Illness'
$.after.bookings[11].CancelledAt: None -> '2026-11-08T23:54:00'
$.after.bookings[11].Channel: 'PARTNER' -> 'PORTAL'
$.after.bookings[11].ExternalRef: 'PTR-R221113' -> None
$.after.bookings[11].LastModified: '2026-11-10T16:07:00' -> '2026-11-08T23:54:00'
$.after.bookings[11].SessionId: 221 -> 446
$.after.bookings[11].Status: 'BOOKED' -> 'CANCELLED'
$.after.sessions[0].BookedOrAttended: 8 -> 7
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 8 -> 9
```

### RT-15-api-book-retake-between-boundaries

- allowed by: [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md) ODD-04 (calendar retake days, portal rule): booking-api's working-day answer `NOT_ELIGIBLE ... 2026-12-18` becomes eligible on 2026-12-07, so the booking is taken
- requests:
  - api_book: `{"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": {"sessionId": 216, "candidateNumber": "NW00000113"}}`

```
$.steps[0].response.body.bookingId: unexpected in replay ('<booking:1>')
$.steps[0].response.body.error: missing in replay (expected 'NOT_ELIGIBLE')
$.steps[0].response.body.message: missing in replay (expected 'Candidate NW00000113 is not eligible to book HPI-ACC1 (RETAKE_WAIT_NOT_ELAPSED), earliest retake date 2026-12-18')
$.steps[0].response.body.status: unexpected in replay ('BOOKED')
$.steps[0].response.status: 409 -> 201
$.steps[0].sessions_after[0].SeatsAvailable: 9 -> 8
$.after.audit: length 0 -> 1
$.after.bookings: length 12 -> 13
$.after.bookings[10].BookedAt: '2026-11-10T16:07:00' -> '<now>'
$.after.bookings[10].BookingId: 3497 -> '<booking:1>'
$.after.bookings[10].ExternalRef: 'PTR-R221113' -> None
$.after.bookings[10].LastModified: '2026-11-10T16:07:00' -> '<now>'
$.after.bookings[10].SessionId: 221 -> 216
$.after.bookings[11].BookedAt: '2026-11-06T23:54:00' -> '2026-11-10T16:07:00'
$.after.bookings[11].BookingId: 3178 -> 3497
$.after.bookings[11].CancelReason: 'Illness' -> None
$.after.bookings[11].CancelledAt: '2026-11-08T23:54:00' -> None
$.after.bookings[11].Channel: 'PORTAL' -> 'PARTNER'
$.after.bookings[11].ExternalRef: None -> 'PTR-R221113'
$.after.bookings[11].LastModified: '2026-11-08T23:54:00' -> '2026-11-10T16:07:00'
$.after.bookings[11].SessionId: 446 -> 221
$.after.bookings[11].Status: 'CANCELLED' -> 'BOOKED'
$.after.sessions[0].BookedOrAttended: 7 -> 8
$.after.sessions[0].LastModified: '2026-11-10T18:00:00' -> '<now>'
$.after.sessions[0].SeatsAvailable: 9 -> 8
```

## Observations (not parity mismatches)

- The harness fix of this PR: `CN-02` and `CN-06` used to flake against modern because the booking/cancel timestamps written by
  scheduling-service's JVM clock could be a few seconds behind the scenario start taken from the DB clock, so they were not
  normalized to `<now>`. `normalize.py` now parses ISO timestamps (fraction, `Z`, offsets) and treats volatile timestamp keys up
  to 30 s before the scenario start as run-time values (`test_runtime_timestamps_from_another_clock_are_normalized`). Both now
  differ only by the classified audit/seat lines above, with no timestamp lines.
- `--settle-url` (new, optional): the modern snapshots are taken after the outbox relay reports `pending: 0`, so a snapshot can
  never race the asynchronous projection into `dbo.Booking/Waitlist/ExamSession` (`test_wait_settled_*`).
- Relay audit rows carry `AuditLog.Actor` = the event's actor (`scheduling-service`), while ADR 0003 "Not changed" says
  `scheduling-relay`. Audit text only (not a Booking/Waitlist/ExamSession field, and the legacy actor `booking-api` differs anyway);
  left unchanged and listed as an open item rather than changed in this evidence PR.
