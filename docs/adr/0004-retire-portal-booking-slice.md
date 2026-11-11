# ADR 0004: retire the .NET booking/capacity slice; legacy booking URLs redirect to /app (MBA-3824, M5)

Status: accepted. Builds on [ADR 0003](0003-single-writer-outbox-relay.md) (M4 single writer) and the M3 routing
gateway ([services/routing-gateway/README.md](../../services/routing-gateway/README.md)).

## Context

Since M4 scheduling-service is the only writer of bookings, waitlist, attendance and capacity for every centre
(`routing.modern-centers=*`, `routing.coexistence-mode=relay`), and every legacy login is denied those writes
(`db/security/040_single_writer.sql`). The portal booking pages, the capacity page, the four booking procs and
booking-api's write SQL were therefore dead code. Their legacy behaviour stays documented in
[README.md](../../README.md), [docs/SYSTEM_OVERVIEW.md](../SYSTEM_OVERVIEW.md) (portal routes, stored procedures)
and [docs/as-is/](../as-is/) (HB-01..HB-31, MR-01..MR-21, deep dives); it is not re-derived here.

## Decisions

1. **Portal slice deleted.** `BookingController` and `Views/Booking/*`, `AdminController.Capacity` (GET/POST),
   `Views/Admin/Capacity.cshtml` and the `AdminCapacity` route, `AssessmentDelivery.Services/Booking/*`,
   `AdminService.ChangeCapacity`/`GetSessionRow`, `SqlErrorHelper` (only translated proc errors), `Models/BookingModels.cs`,
   `Business/Rules/SeatCounter.cs` and `SeatCounterTests`. The two reads that `/Eligibility` and `/Results` borrowed
   from `IBookingService` moved to `IEligibilityService.ListExams` and `IResultsService.FindCandidate` (same queries).
   `BookingStatus` constants moved to `Business/Rules/BookingStatus.cs`; the admin "Booked" column is counted in the
   EF query (BOOKED + ATTENDED, same rule). `/Admin/Centers`, `/Admin/Centers/{id}/Edit`, the `/Admin/Sessions` list,
   roster CSV, capacity audit, `/Eligibility` and `/Results` are unchanged. The "Bookings" tab, the candidate
   quick-find and the per-session "Capacity" links now point at the legacy URLs, which the gateway redirects.
2. **Every centre redirects.** The gateway no longer consults the allow-list for the retired portal pages: they
   redirect to `/app` for every centre, including centres never allow-listed in M3, unresolvable ids and requests
   without a centre key. GET/HEAD answer `302`, POST `303`; `X-Route: modern`, log reason `legacy page retired`.

   | Legacy URL | Redirect |
   |---|---|
   | `/Booking[?CenterCode=&ExamCode=&From=&To=]` | `/app/[?center=&exam=&from=&to=]` |
   | `/Booking/Book?sessionId=`, `POST /Booking/Book` (form `SessionId`) | `/app/book?sessionId=` (`/app/` without one) |
   | `/Booking/MyBookings[?candidateNumber=]` | `/app/my-bookings[?candidateNumber=]` (the UI loads that candidate) |
   | `/Booking/Cancel/{id}` | `/app/my-bookings` |
   | `/Booking/Reschedule/{id}` | `/app/reschedule/{id}` |
   | `/Admin/Sessions?centerId=` | `/app/admin/centers/{centerCode}` (`/app/` if the id does not resolve) |
   | `/Admin/Sessions/Capacity/{id}` | `/app/admin/sessions/{id}` |

   Directly on :8080 these URLs are now `404`. `routing.coexistence-mode` stays `relay`.
3. **booking-api writes are routed entirely by the gateway.** `POST /api/v1/bookings`, `DELETE /api/v1/bookings/{id}`
   and `POST /api/v1/bookings/{id}/reschedule` are served from scheduling-service by the gateway (M3/M4, unchanged).
   booking-api's own write path (`BookingService`, the write DAO methods, request/response models) is deleted; a write
   that reaches :8081 directly gets `410 {"error":"LEGACY_WRITE_RETIRED"}` (was `403 LEGACY_WRITE_DENIED` in M4).
   Reads (`GET /api/v1/sessions`, `/bookings/{id}`, `/candidates/**`) are unchanged.
4. **Procs dropped.** `db/procs/090_retire_booking_procs.sql` drops `usp_BookSeat`, `usp_CancelBooking`,
   `usp_ChangeCapacity` and `usp_RescheduleBooking` (idempotent). Their create scripts `020`..`023` are deleted and kept
   in git history (tag `legacy-baseline`). The `DENY EXECUTE` lines on them leave `040_single_writer.sql`.
   `db/checks/test_usp_GetCapacityAudit.sql` now writes its capacity changes the way `usp_ChangeCapacity` did.
5. **Guard.** `scripts/check-retired-booking-writes.sh` (CI: `.github/workflows/retired-booking-writes.yml`) fails if
   portal C# or booking-api main code references the four procs, updates `SeatsAvailable` or writes `Booking`/`Waitlist`.

## Expected characterization differences (for M6 parity)

The M1 transcripts in `characterization/transcripts/` are not edited. Replayed after M5 they differ as follows;
classify these as **intended (MBA-3824)**, not regressions.

| Class | Transcripts | Through :8000 | Directly against legacy |
|---|---|---|---|
| M5-PORTAL-REDIRECT: a step calls a retired portal page (`POST /Booking/Book`, `/Booking/Cancel/{id}`, `/Booking/Reschedule/{id}`, `/Admin/Sessions/Capacity/{id}`) | AC-01, AC-03, AC-05, AC-07, BK-01, BK-03, BK-05, BK-07 (setup), BK-08, BK-10, CN-01, CN-03, CN-05, CN-07, CP-01, CP-02, CP-03, CP-04, CP-05, CP-07, CP-08, MX-03, RS-01, RS-03, RS-05, RS-07 (setup), RS-08, RS-10, RT-14 (29) | `303 See Other` (`302` for GET) to the `/app` route above, `X-Route: modern`; that step writes nothing, so later `after` snapshots differ | `404` |
| M5-PROC-DROPPED: a db step executes a retired proc | BK-07, CP-06, CP-09, RS-07 | SQL error 2812 `Could not find stored procedure` instead of the proc's error 50000 | same |
| M5-API-WRITE-410: an api step writes on booking-api | AC-02, AC-04, AC-06, AC-08, BK-02, BK-04, BK-06, BK-09, BK-11, CN-02, CN-04, CN-06, CN-08, CP-07, MX-03, RS-02, RS-04, RS-06, RS-08, RS-09, RT-15 | unchanged from M4 (served by scheduling-service in the v1 shape) | `410 LEGACY_WRITE_RETIRED` (M4: `403 LEGACY_WRITE_DENIED`) |
| M5-MIXED: `scripts/mixed-traffic.ps1` workload | MX-01 | as M4 when `PORTAL_URL`/`BOOKING_API_URL` point at :8000 | portal `404`, partner `410` |
| unchanged | RT-01..RT-13 (eligibility pages and API reads), MX-02 | same as M4 | same as M4 |

## Consequences

- The slice has one implementation. Removing a centre from `routing.modern-centers` no longer brings back portal
  booking pages (they do not exist); it only moves API traffic, and since M4 legacy writes are denied anyway. Narrowing
  the allow-list now freezes writes for the removed centres (v2 `403 CENTER_NOT_MODERN`, v1 forwarded to booking-api `410`).
- Through :8000, `/Admin/Sessions?centerId=` opens the modern admin grid (as it already did for allow-listed centres since
  M3), so the legacy session list and its roster CSV link are reached directly on :8080 or by URL
  (`/Admin/Sessions/Roster/{id}?format=csv` and `/Admin/Centers/{id}/CapacityAudit` still proxy to legacy).
- `SeatsAvailable` is still read by the admin pages; it is written only by the relay (ADR 0003).
