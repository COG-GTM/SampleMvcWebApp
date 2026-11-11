# Partner booking path: scheduling-service is the single writer (Session 4)

booking-api (`services/booking-api`) no longer reads or writes `ExamSession`, `Booking` or `Waitlist`. Every partner call goes to scheduling-service (`scheduling.base-url`, default `http://localhost:8084/api/v1`, env `SCHEDULING_BASE_URL`) with `channel=PARTNER` and `X-Actor: booking-api`.

| Partner route (unchanged) | Before | Now |
|---|---|---|
| `POST /api/v1/bookings` | pre-checks, then direct `INSERT dbo.Booking` + `UPDATE ExamSession SET SeatsAvailable -= 1` (or `INSERT dbo.Waitlist` with `MAX(Position)+1`) | same pre-checks, then one `POST /bookings` |
| `DELETE /api/v1/bookings/{id}` | `EXEC usp_CancelBooking` | `POST /bookings/{id}/cancel` |
| `POST /api/v1/bookings/{id}/reschedule` | cancel proc, then the partner insert path | one `POST /bookings/{id}/reschedule` (`channel=PARTNER`, keeps `externalRef`, HB-11) |
| `GET /api/v1/bookings/{id}`, `GET /api/v1/candidates/{n}/bookings` | SQL joins | `GET /bookings/{id}`, `GET /bookings?candidateId=` |
| `GET /api/v1/sessions`, `GET /api/v1/sessions/{id}` | SQL joins, `bookedSeats` counted in booking-api | `GET /sessions`, `GET /sessions/{id}` (`bookedSeats` comes from the service) |

Paths, payloads, status codes and the `{error, message}` error body are the same. Pre-checks still run in booking-api, in the legacy order and with the legacy messages: session OPEN, duplicate booking, accommodation seat, retake eligibility (the service does not check retake eligibility, HB-08). If the service rejects a write afterwards (for example, a race lost under its session lock), its error code maps back to the same partner message.

**Intended behaviour change (HB-01):** a partner booking that gets a seat used to decrement `SeatsAvailable` twice: once from booking-api and once from `trg_Booking_SeatSync`. scheduling-service now decrements once. Seat drift after partner bookings is 0. Nothing else in the partner contract changes.

New error: scheduling-service unreachable (connection failure, timeout) or a 5xx without a contract error body returns `503 {"error":"SCHEDULING_UNAVAILABLE"}`. A service `SYSTEM_ERROR` (SQL failure such as deadlock 1205) returns `500 {"error":"DB_ERROR","message":<SQL message>}`, the same as a SQL failure on the legacy direct path.

The same root cause also took two seats on the target of a partner reschedule (HB-11). That also goes away; the rest of HB-11 stays (partner semantics, `externalRef` copied, retake re-checked).

Concurrent partner bookings could get duplicate waitlist positions under the old unlocked `MAX(Position)+1` read (HB-29). Waitlist positions are now assigned by scheduling-service under its session lock. Whether HB-29 is still reproduced is up to that implementation and the characterization suite.
