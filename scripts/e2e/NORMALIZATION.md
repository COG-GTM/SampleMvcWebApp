## Cutover diff normalization

Only two kinds of transformation are applied before the legacy and migrated snapshots are compared
(`scripts/e2e/cutover_diff.py`). Every other column of every row is compared exactly.

| Id | What | Rule | Why |
|---|---|---|---|
| N1 | Surrogate ids created during the workload | `BookingId` / `WaitlistId` greater than the pre-workload `MAX` on that side become `n1, n2, ...` in id order. References follow them: `Waitlist.BookingId`, `AuditLog.EntityId` where `EntityName='Booking'`, and `booking=<id>` inside `AuditLog.Detail`. | Identity values are allocated independently by each database. Ids that existed before the workload (seed, preserved by the import) are compared verbatim. |
| N1a | `AuditLog.AuditId` | Not compared; audit rows are compared as a multiset of `EntityName, EntityId, Action, Detail, Actor, LoggedAt`. | The migrated stack writes Booking/ExamSession audit rows to `Scheduling.dbo.AuditLog` and the others (Result, Exam, Candidate) to `AssessmentDelivery.dbo.AuditLog`, so they come from two identity sequences. The legacy side's single table is compared against the union of both. |
| N2 | Timestamps written during the workload | `DATETIME` values (`StartsAt, LastModified, BookedAt, CancelledAt, AddedAt, LoggedAt`) at or after the workload start on that side become `<run>`. | Wall-clock values differ between two runs. Older values (seed / import) are compared verbatim, so an import that shifted a timestamp shows up as drift. NULL vs non-NULL is still compared. |

Columns compared: the legacy schema of `ExamSession`, `Booking`, `Waitlist` and `AuditLog`
(`db/schema/010_tables.sql`). `ExamSession.RowVer` is a migrated-only `ROWVERSION` concurrency token and is not
part of the legacy row, so it is not selected.

What counts as drift: any row only on one side, any changed row, any audit-row multiset difference, any operation whose
outcome differs between the two runs, any baseline difference between the production copy and the import, or inputs
chosen differently. The single permitted exception is a row-by-row justified intentionally removed defect: an
`ExamSession` row whose only difference is `SeatsAvailable`, by exactly the number of PARTNER bookings inserted BOOKED on
that session (HB-01 removed). The rehearsal workload is built so that removing HB-01 cannot cascade into different
BOOKED/WAITLISTED outcomes: partner bookings go onto sessions with spare seats, or onto a session whose waitlist the
portal has already started.
