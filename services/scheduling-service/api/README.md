# scheduling-service API contract (MBA-3833)

| file | what | enforced by |
|---|---|---|
| `openapi.yaml` | public `/api/v2` contract, exactly what the running service serves at `GET /v3/api-docs` (UI: `/swagger-ui.html`) | `OpenApiSpecIT` fails on any drift |
| `baseline/openapi-v2.0.0.yaml` | published v2 baseline (first publication) | `OpenApiCompatibilityTest` (openapi-diff) fails on breaking changes vs every baseline |
| `CONTRACTS.md` | consumer-driven contracts (routing gateway, modern UI) | generated `*ContractIT` provider tests |
| `../frontend/src/api.contract.ts` | UI types vs `openapi.yaml` (openapi-typescript) | `npm run typecheck` / `npm run build` (part of `mvn verify`) |

Documentation (summaries, tags, error responses with examples, nullability, the error table) is added by
`web/OpenApiConfig` and `springdoc.properties`; `/api/v2/internal/**` and the `/api/v1` facade are not in the public spec.

## Changing the API

1. Change the code, then regenerate the committed spec and review the diff:
   `./mvnw -B verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OpenApiSpecIT -Dopenapi.write=true`
2. `./mvnw -B clean verify`. Additive changes pass. Removing an endpoint/operation, removing a response field or adding a
   required request field fails `OpenApiCompatibilityTest` with an openapi-diff report.
3. A deliberate breaking change is a new major version: add a new baseline file once partners have signed off.
   Never edit an existing baseline to make the build pass.

Use `clean verify` after editing contracts: the generated `*ContractIT` sources are only reliably recompiled from clean.

## Error model

`ApiError` = `{error, message, legacyCode}`: `error` is the symbolic code (booking-api's `{error, message}` vocabulary,
kept so v1 and v2 share it), `legacyCode` = 50000 + the legacy proc's RAISERROR state (ODD-11, HB-14, ADR 0002),
e.g. `NO_ACCOMMODATION_SEAT` = 50003, null when the rule never existed as a proc error. `/api/v1` errors stay
`{error, message}`.
