# Portal cross-boundary contract spec (.NET side)

**Limitation.** The portal is ASP.NET MVC 5 on .NET Framework 4.8 with EF6. It runs only on Windows (`scripts/run-legacy.ps1`), and Phase 0 contract tests run on Linux. The portal's cross-boundary behaviour is therefore pinned in three ways, none of which runs the portal's own C# data-access code:

1. **SQL-level twins of the EF LINQ queries**, in `services/scheduling-contract-tests/.../PortalQueries.java`, executed against the real schema by `PortalReadContractTest`. Each twin keeps the LINQ's predicates, joins, projected columns and ordering. String equality runs server-side, so collation behaves as it does under EF. A drift in the C# source fails `InventoryGuardTest`, which checks the exact LINQ lines.
2. **The real business rule** `AssessmentDelivery.Business/Rules/RetakeRules.cs` (with `IRetakeRules`, `PriorAttempt`, `RetakeDecision`), compiled with Mono `mcs` and executed by `RetakeEligibilityContractTest.portalRealCSharp`. Its input is the attempts read with the portal's `EligibilityService` query, and the culture is `en-GB`, as in `Web.config`. If `mcs`/`mono` are missing, those 10 tests are *skipped* (never silently passed), and the Java transliteration still runs.
3. **The proc call surface** the portal relies on (error 50000, severity 16, state 1-6, exact text), pinned by `PortalReadContractTest.procErrorSurface`.

What is **not** covered on Linux:
- EF6's generated SQL itself (only its semantics are reproduced).
- The MVC controllers and views.
- `SqlErrorHelper` turning SqlException into `PortalException`.
- The `en-GB` model binding of dates.
- The blank-accommodation default (HB-09) applied in `BookingService.Book` before the proc call.

Phase 2 (Session 4) should run the matching checks below on the Windows box against both the legacy build and the rewired build. They are written for NUnit in `AssessmentDelivery.Tests`, using the existing `IntegrationTests` fixtures.

| # | Portal method | Given | Expect (legacy = rewired) |
|---|---|---|---|
| S-1 | `BookingService.FindOpenSessions(criteria)` | OPEN sessions in the past and future, one CLOSED | only OPEN, past included (HB-31); order `StartsAt, CenterCode`; `To` covers the whole day; `SessionListItem` fields equal |
| S-2 | `BookingService.GetSession(id)` | CLOSED session | returned (no status filter) |
| S-3 | `BookingService.GetBookingsForCandidate(" nw000001 ")` | bookings in several statuses | trimmed and upper-cased lookup; all statuses; order `StartsAt DESC, BookingId DESC`; `CanChange` true only for BOOKED/WAITLISTED |
| S-4 | `BookingService.FindRescheduleTargets(bookingId)` | same-exam sessions: OPEN past, OPEN future, CLOSED, current | only OPEN, future (`>= DateTime.Today`), not current |
| S-5 | `BookingService.Book(session, number, "")` | candidate default `ET25` | booking stored with `ET25` (HB-09); outcome text unchanged |
| S-6 | `BookingService.Book` error paths | closed session / duplicate / no ET seat | `PortalException` with the state-1/2/3 text from `scheduling-service-contract.md` section 4 |
| S-7 | `BookingService.Cancel(id)` on CANCELLED | - | `PortalException("Booking {id} cannot be cancelled.")` |
| S-8 | `BookingService.Reschedule(partnerBooking, newSession)` | PARTNER booking with `ExternalRef` | new booking PORTAL, `ExternalRef` null (HB-10) |
| S-9 | `AdminService.ChangeCapacity(id, 0)` with AccommodationCapacity 1 | - | `PortalException("Invalid capacity 0 for session {id}.")` |
| S-10 | `AdminService.ListSessions(centerId)` | BOOKED, ATTENDED, WAITLISTED with and without Waitlist row | `Booked` = BOOKED+ATTENDED; `WaitlistCount` = Waitlist rows |
| S-11 | `AdminService.GetRoster(id)` / `GET /Admin/Sessions/Roster/{id}?format=csv` | mixed statuses, orphan WAITLISTED | byte-identical CSV; seat holders then waitlist; null position for orphan |
| S-12 | `EligibilityService.Check(number, examCode, date)` | every row of `retake-cases.psv` | `Eligible`, `Reason`, `EarliestRetakeDate` equal to the portal columns |
