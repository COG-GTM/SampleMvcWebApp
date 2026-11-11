# routing-gateway (MBA-3822, M3)

Strangler facade for the Northwind Assessment Network scheduling slice. Java 21 / Spring Boot 3.3 / Spring Cloud
Gateway 2023.0, port **8000**. Clients and partners point at `:8000` instead of the portal (`:8080`) or booking-api
(`:8081`); nothing else changes for them. Standalone Maven project: it is **not** part of `services/pom.xml`
(the Java 8 / Boot 1.5 aggregator).

## Run

```bash
scripts/run-legacy.sh                    # SQL Server + booking-api :8081, results-service :8082, capacity-sync :8083
scripts/run-routing-gateway.sh start     # builds with Java 21, starts :8000 (stop | status | run)
```

Build/test only: `cd services && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw -f routing-gateway/pom.xml verify`
(set `MVNW_REPOURL=https://maven-central.storage-download.googleapis.com/maven2` if Maven Central rate-limits).

## Routing

| Path | Upstream | Rule |
|---|---|---|
| `/app`, `/app/**`, `/api/v2/**` | scheduling-service `:8090` | modern UI + its relative API calls; v2 writes (`POST /api/v2/bookings`, `DELETE`/`reschedule`/`offer/accept|decline` on a booking, `POST /api/v2/offers/{offerId}/accept|decline` resolved via `/internal/routing/resolve?offerId=` to the offered booking's center, `PUT .../capacity`) only for allow-listed centers or modern-owned bookings, else `403 CENTER_NOT_MODERN` |
| `/api/v1/results/**` | results-service `:8082` | unchanged |
| `/sync/**` | capacity-sync `:8083` | unchanged |
| `/api/v1/**` | booking-api `:8081` | strangler, see below |
| everything else | portal `:8080` | strangler, see below |

**booking-api** (center resolved through scheduling-service `GET /internal/routing/resolve`):

| v1 request | resolved by | modern translation (v1 shape is restored on the way back) |
|---|---|---|
| `GET /api/v1/sessions?centerCode=` | centerCode | `GET /api/v2/centers/{centerCode}/sessions?examCode=&from=&to=` -> `[SessionInfo]` (`bookedSeats` null, as v1 lists). v1 `to` includes the whole day, v2 `to` is exclusive, so the gateway sends `to + 1 day`; a code with leading blanks stays legacy (legacy SQL `=` never matches it), trailing blanks are stripped |
| `GET /api/v1/sessions/{id}` | sessionId | `GET /api/v2/sessions/{id}` -> `SessionInfo` |
| `GET /api/v1/bookings/{id}` | bookingId | `GET /api/v2/bookings/{id}` -> `BookingInfo` |
| `POST /api/v1/bookings` | body `sessionId` | `POST /api/v2/bookings` with `channel=PARTNER` -> 201 `{bookingId,status}` |
| `DELETE /api/v1/bookings/{id}?reason=` | bookingId | `DELETE /api/v2/bookings/{id}?reason=` (default `Cancelled by partner`) -> `BookingInfo` |
| `POST /api/v1/bookings/{id}/reschedule` | bookingId | `POST /api/v2/bookings/{id}/reschedule` -> **200** `{bookingId,status}` (v2 answers 201) |

v2 errors `{error,message,legacyCode}` become v1 `{error,message}` with the same status. Bodies are written the way
booking-api writes them (Jackson pretty printer, v1 field order, explicit nulls, `yyyy-MM-ddTHH:mm:ss`,
`Content-Type: application/json;charset=UTF-8`). Candidate bookings/eligibility and session lists without
`centerCode` are not center-specific and stay legacy.

**Portal** (paths case-insensitive, like MVC routing; GET answers 302, POST answers 303). The .NET booking and
capacity pages are retired (MBA-3824), so their URLs redirect for **every** center, allow-listed or not, with or
without a center in the URL (`RetiredPortalLinks`). The center lookup only labels the route log
(`reason=legacy-retired`); if it fails the redirect still happens.

| Portal page (retired) | logged center from | modern deep link |
|---|---|---|
| `/Booking`, `/Booking/Index` (search form) | CenterCode, if given | `/app[?centerCode=&examCode=&date=<From>]` |
| `/Booking/Book?sessionId=` (GET or form POST) | sessionId | `/app/book?sessionId=[&candidateNumber=]` |
| `/Booking/Cancel/{id}` | bookingId | `/app/my-bookings[?candidateNumber=]` (the contract has no cancel deep link) |
| `/Booking/Reschedule/{id}` | bookingId | `/app/reschedule/{id}[?candidateNumber=]` |
| `/Booking/MyBookings` | centerCode, if given | `/app/my-bookings[?candidateNumber=]` |
| `/Admin/Sessions/Capacity/{id}` | sessionId | `/app/admin/sessions/{id}` |
| any other `/Booking/...` URL | centerCode, if given | `/app` |

`/Admin/Sessions?centerId=` (still a portal page) goes to `/app/admin/centers/{centerCode}` only for allow-listed
centers. `/Admin/Centers`, `/Eligibility`, `/Results` and every other page pass through.

**Decision** (`RouteDecider`, a pure class): booking id `>= routing.modern-booking-id-floor` (50,000,000) or
`bookingOrigin=MODERN` -> modern regardless of the allow-list (in-flight modern bookings are never orphaned);
else center on `routing.modern-centers` (writes: also `routing.modern-write-centers`; `*` = every center) -> modern; else legacy. Every routed response gets `X-Route: legacy|modern`
and one log line `route=<legacy|modern> center=<code|-> reason=... method=... path=... status=...` (logger `routing`).

**Failure behaviour.** With an empty allow-list no lookup is made (modern-range booking ids aside), so the gateway
is transparent even if scheduling-service is down. With a non-empty list, a failed lookup sends reads to legacy and
fails writes closed with `503 {"error":"ROUTING_UNAVAILABLE",...}` (a write must never land in the wrong system).
A lookup 404 means "unknown to modern" -> legacy. Lookups are cached for the `Cache-Control: max-age` the resolve
endpoint returns (60 s for center/session); booking lookups are not cached unless `routing.resolve-default-ttl` > 0.

## Allow-list and rollback

**M4 (single writer).** `config/routing.properties` ships `routing.modern-write-centers=*` and
`routing.coexistence-mode=relay`: every booking/capacity write (v1 partner API, portal form posts, direct `/api/v2`)
goes to scheduling-service, which commits it with an outbox event; the relay projects it into `AssessmentDelivery`
as `ad_sched_relay`, so legacy reads (portal pages, v1 GETs) keep rendering from the legacy tables. The legacy logins
are DENYed booking/seat writes (`db/security/040_single_writer.sql`), so removing `*` no longer gives a working
legacy write path; roll back by restoring the pre-M4 database grants first.
Portal form posts are not applied in place: like any routed center, a booking/cancel/reschedule/capacity POST answers
303 to the pre-filled modern deep link (table above) and the user confirms there; the portal's own EF6 write would be
denied by 040. Applying the form inside the portal needs the .NET portal to call `/api/v2` itself (Windows, later story).

| Property | Default | Meaning |
|---|---|---|
| `routing.modern-centers` | *(empty)* | `TestCenter.CenterCode`s whose booking/capacity traffic goes to scheduling-service |
| `routing.test-centers` | `BOS01,SEA01,AKL01` | seeded centers that count as test/demo centers |
| `routing.modern-write-centers` | *(empty)* | centers whose booking/capacity **writes** go to scheduling-service while reads stay legacy; `*` = all centers (M4 sets `*` in `config/routing.properties`) |
| `routing.coexistence-mode` | `none` | `relay` once the M4 outbox relay is live (M4 sets it in `config/routing.properties`) |
| `routing.config-file` | `config/routing.properties` | watched allow-list file (the start script passes `$ROUTING_CONFIG_FILE`, default `services/routing-gateway/config/routing.properties`; do not repeat `--routing.config-file` in `GATEWAY_ARGS`, Spring would join both values) |
| `routing.watch-interval` | `1s` | file poll interval |
| `routing.*-url` | `localhost:8080/8081/8082/8083/8090` | upstreams |

The three allow-list properties are read from the file when it defines them, else from normal Spring config
(application.properties, `--args`, env). The list is re-read when the file changes (within `routing.watch-interval`)
and on `POST /actuator/refresh`. **Rollback = remove the center from `routing.modern-centers` and save** (or call
refresh): the next request for that center goes to legacy, no restart. Measured in the functional tests: < 1.5 s.

**Test/demo-center rule.** Until M4 relays modern writes back into `AssessmentDelivery`, only test/demo centers
may be allow-listed. Test/demo centers are `BOS01` (Harborview Testing Centre, NA-EAST; the contract's example
center and C1 in the functional tests), `SEA01` (Puget Testing Rooms, NA-WEST) and `AKL01` (Waitemata Exam Rooms,
APAC), the latter two among the lowest-volume seeded centers in their regions; all three have open future sessions.
`NYC02` (a production center) is C2 in the tests. Change `routing.test-centers` to redefine it. If `routing.modern-centers` or `routing.modern-write-centers` contains any other center (or `*`) and
`routing.coexistence-mode` is not `relay`, the gateway **refuses to start**; a refresh that would introduce such a
center is rejected (logged at ERROR) and the previous list stays in force.

## Tests

* `mvn test` (Java 21): `RouteDeciderTest`, `AllowListPolicyTest`, `V1ShapesTest` (unit), `StartupGuardTest`,
  `GatewayIntegrationTest` (full gateway against WireMock stand-ins for portal, booking-api, results-service,
  capacity-sync and scheduling-service, incl. rollback via the watched file in < 5 s).
* `functional/run-functional-tests.sh`: gateway jar against the **real** legacy services + SQL Server, the **real**
  scheduling-service (`SCHEDULING=real`, default: restarted by `scripts/run-scheduling-service.sh` on a fresh
  `Scheduling` database imported from a fresh `AssessmentDelivery`; modern bookings are checked in `Scheduling.dbo.booking`)
  or `functional/scheduling_stub.py` (`SCHEDULING=stub`, contract items 3, 5, 6 incl. a tiny `/app` SPA), and
  `functional/portal_stub.py` (MVC routes/field names of the portal). Covers transparency (reads byte-identical
  direct vs `:8000`; writes replayed after a DB reset), C1/C2 booking, portal redirect, ownership, rollback (file and
  refresh), the startup guard and rejected refresh. It **rebuilds `AssessmentDelivery`** (`db/apply.sh`) several
  times and, with `SCHEDULING=real`, drops and re-creates `Scheduling`.

The portal stub stands in for the Windows-only .NET portal.
