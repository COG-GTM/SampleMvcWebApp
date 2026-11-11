# Consumer contracts (MBA-3833): `src/test/resources/contracts/`

Spring Cloud Contract YAML contracts, one folder per consumer of scheduling-service. `mvn verify` generates
`*ContractIT` provider-verification tests from them (`spring-cloud-contract-maven-plugin`, EXPLICIT mode: real HTTP
against the booted service on Testcontainers SQL Server, fixture data from `SqlServerTestBase.FixtureLegacy`,
inventory reset before each contract) and failsafe runs them.

| folder | consumer | base class |
|---|---|---|
| `routing_gateway/` | strangler routing gateway (M3): the `/api/v1` facade partners still call (booking-api shapes, `docs/as-is/architecture.md`) and the `/api/v2` calls it translates them to | `RoutingGatewayContractBase` |
| `scheduling_ui/` | modern UI under `/app` (`frontend/src/api.ts`) | `SchedulingUiContractBase` |

A consumer changing what it needs edits its folder here (consumer-driven); a provider change that breaks a contract fails
the build. Exact v1 key sets (`{bookingId, status}`, `{eligible, reason, earliestRetakeDate}`) are additionally pinned
by `RoutingGatewayContractBase` and `LegacyV1ShapesIT`, since contract bodies only assert presence.
