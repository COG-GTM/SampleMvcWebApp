# routing-gateway (M3, MBA-3822)

Strangler routing layer on **:8000** in front of the legacy portal (:8080) and booking-api (:8081). Booking and
capacity traffic for the test centres on an allow-list goes to `services/scheduling-service` (:8090); everything
else goes to legacy unchanged. Clients and partners point at :8000 instead of :8080/:8081.

Standalone Java 21 / Spring Boot 3.3 / Spring Cloud Gateway 2023.0 module with its own `mvnw`; it is not part of
the Java 8 aggregator in `services/pom.xml`. Legacy behaviour referred to below is documented in
`docs/SYSTEM_OVERVIEW.md` and `docs/as-is/` (HB-xx, MR-xx); it is not re-derived here.

## Run

```bash
# Windows (Git Bash) or PowerShell; legacy estate + scheduling-service first
powershell -ExecutionPolicy Bypass -File scripts/run-legacy.ps1 < /dev/null
powershell -ExecutionPolicy Bypass -File scripts/run-scheduling-service.ps1 < /dev/null
powershell -ExecutionPolicy Bypass -File scripts/run-routing-gateway.ps1 < /dev/null   # -Status, -Stop, -SkipBuild
# Linux
"$BASH" scripts/run-routing-gateway.sh            # start|stop|status
```

Tests: `cd services/routing-gateway && ./mvnw -B test` (unit + `GatewayIntegrationTest` against stub upstreams).

| Setting (env var) | Default |
|---|---|
| `routing.portal-url` (`ROUTING_PORTAL_URL`) | `http://localhost:8080` |
| `routing.booking-api-url` (`ROUTING_BOOKING_API_URL`) | `http://localhost:8081` |
| `routing.results-service-url` / `routing.capacity-sync-url` | `:8082` / `:8083` |
| `routing.scheduling-service-url` (`ROUTING_SCHEDULING_SERVICE_URL`) | `http://localhost:8090` |
| `routing.modern-centers` (in `config/routing.properties`, file = `ROUTING_CONFIG_FILE`) | empty |
| `routing.test-centers` | `BOS01,SEA01,AKL01` |
| `routing.coexistence-mode` (`ROUTING_COEXISTENCE_MODE`) | `none` |
| `routing.modern-booking-id-floor` | `50000000` |

## Allow-list, reload and rollback

`config/routing.properties` holds `routing.modern-centers` (comma-separated `TestCenter.CenterCode`). It is
reloaded without a restart, either by `ConfigFileWatcher` (polls the file every second) or by
`POST /actuator/refresh`. **Rollback = remove the code from the list and save** (or refresh): the next request
for that centre goes to legacy, typically within 1-2 s. An invalid list on reload is rejected (logged) and the
previous list stays in force.

**Coexistence guard (until M4).** Legacy and scheduling-service still have separate seat inventories with no
write relay between them (M4 adds it), so only the demo/test centres in `routing.test-centers` (BOS01, SEA01,
AKL01) may be allow-listed. The gateway **refuses to start** if `routing.modern-centers` lists any other centre
unless `routing.coexistence-mode=relay` is set; M4 sets it once the relay is live (`AllowListPolicy`,
`StartupGuardTest`).

## What is routed where

**Retired portal pages (MBA-3824, M5).** The portal no longer has booking or capacity pages, so the seven portal rows
below redirect for **every** centre, whatever the allow-list says, and also without a centre key or when the key does
not resolve (`/app/`, or `/app/my-bookings?candidateNumber=` for `/Booking/MyBookings?candidateNumber=`). The
allow-list still applies to the API rows. See [ADR 0004](../../docs/adr/0004-retire-portal-booking-slice.md).

| Request | Centre from | Allow-listed centre | Otherwise |
|---|---|---|---|
| `GET /Booking?CenterCode=` | CenterCode | 302 `/app/?center=..&exam=..&from=..&to=` | same (M5) |
| `GET /Booking/Book?sessionId=`, `POST /Booking/Book` (form `SessionId`) | sessionId | 302/303 `/app/book?sessionId=` | same (M5) |
| `/Booking/Reschedule/{id}` | bookingId | `/app/reschedule/{id}` | same (M5) |
| `/Booking/Cancel/{id}` | bookingId | `/app/my-bookings` | same (M5) |
| `GET /Booking/MyBookings[?candidateNumber=]` | - | `/app/my-bookings[?candidateNumber=]` | same (M5) |
| `GET /Admin/Sessions?centerId=` | centerId | `/app/admin/centers/{centerCode}` | same (M5) |
| `/Admin/Sessions/Capacity/{id}` | sessionId | `/app/admin/sessions/{id}` | same (M5) |
| `GET /api/v1/sessions?centerCode=` | centerCode | `/api/v2/centers/{c}/sessions` in v1 shape | booking-api |
| `GET /api/v1/sessions/{id}` | sessionId | `/api/v2/sessions/{id}` in v1 shape | booking-api |
| `POST /api/v1/bookings` | body `sessionId` | `POST /api/v2/bookings` (channel PARTNER), v1 `{bookingId,status}` 201 | booking-api |
| `GET`/`DELETE /api/v1/bookings/{id}`, `POST .../{id}/reschedule` | bookingId | `/api/v2/bookings/...` in v1 shape | booking-api |
| `/app/**`, `/api/v2/**` | - | scheduling-service | scheduling-service |
| `/api/v1/results/**`, `/sync/**`, every other portal page | - | results-service / capacity-sync / portal | same |

API requests without a centre key (e.g. `/api/v1/sessions` without `centerCode`) stay on legacy. Portal pages without a modern equivalent (`/Admin/Centers`, `/Eligibility`,
`/Results`, roster CSV, capacity audit) always stay on legacy.

The centre is resolved through scheduling-service `GET /api/v2/internal/routing/resolve?centerCode=|centerId=|sessionId=|bookingId=`
(reads its snapshots; cached for `routing.resolve-cache-ttl`, 30 s). If the lookup fails the request goes to
legacy (writes through the v2 guard below are refused instead). `/api/v2/internal/**` (resolver, snapshot
import/reset) is never proxied: the gateway answers `404` for it. With an **empty allow-list no lookups are made and no request bodies are read**: the gateway is a
transparent proxy (verified by replaying the M1 transcripts through :8000, see the PR).

**Ownership by booking origin.** scheduling-service raises its booking identity to
`scheduling.booking-id-floor` (50000000) on import, so ids at or above `routing.modern-booking-id-floor` were
created on the modern side. Cancel/reschedule/get for such ids always goes to scheduling-service, whatever the
allow-list says, so rolling a centre back never orphans an in-flight modern booking. Lower ids follow the centre
rule above.

**Modern write guard.** v2 writes that reach scheduling-service through :8000 (`POST /api/v2/bookings`,
`DELETE /api/v2/bookings/{id}`, `POST /api/v2/bookings/{id}/reschedule`, `PUT /api/v2/sessions/{id}/capacity`)
are refused with `403 {"error":"CENTER_NOT_MODERN"}` when the centre is not allow-listed (modern-owned bookings
excepted), so the modern UI cannot take seats legacy still owns. The guard fails closed: if a centre cannot be
resolved (lookup outage, unknown id) the write is refused with the same 403. A reschedule must also land on an
allow-listed centre; the one exception is a modern-owned booking moving within its own centre after that centre
was rolled back. The same rule applies to v1 reschedules (`403 CENTER_NOT_MODERN`), and a legacy booking cannot be
moved onto an allow-listed centre's session (`403 CENTER_IS_MODERN`).

Every request is logged as `route=legacy|modern center=<code|-> METHOD path (reason)` and the response carries
`X-Route: legacy|modern`.
