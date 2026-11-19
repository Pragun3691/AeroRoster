# Phase 3.9 - Frontend API integration foundation

## Scope

This phase adds typed, centralized Axios boundaries only. Existing pages remain explicitly demo-only and do not import or call the services. It is not live integration or production-readiness work.

## Architecture

`frontend/src/api/` now separates `config.ts` (Vite URL and timeout), `client.ts` (one Axios instance), `errors.ts` (sanitized typed failures), `versioning.ts` (conditional token construction), `connectivity.ts` (cached reachability probe), and `services/` (contract-scoped requests). `src/types/api.ts` maps the frozen 1.1.0 DTO/enums/page shapes. UI components stay free of HTTP logic.

## Environment and setup

Copy `frontend/.env.example` to `frontend/.env` and set `VITE_API_BASE_URL`. Local development defaults to `http://127.0.0.1:8080/api/v1`, matching the documented loopback API. `.env` is ignored. A deployed frontend must use an approved same-origin deployment arrangement: the backend rejects cross-origin requests, and this phase does not add a proxy or weaken CORS.

## Service inventory

- `crewService`: `/crew`, record/activity operations, crew assignment history.
- `aircraftService`: `/aircraft`, activity operations, aircraft flights.
- `airportService`, `aircraftTypeService`: documented reference endpoints.
- `qualificationService`: crew-held qualifications and expiry/revoke operations.
- `flightService`: `/flights`, status/cancellation, flight assignment history and coverage.
- `assignmentService` and `eligibilityService`: eligibility, create/get/cancel/reassign; deliberately no invented global assignment list.
- `coverageService`: documented flight coverage endpoint.
- `unavailabilityService`: crew-scoped periods and conflict check.

All service URLs and response types correspond to the frozen OpenAPI 1.1.0 contract. API DTOs use `BackendCrewRole` and its contract value `COPILOT`; the existing demo-only `CrewRole` with `CO_PILOT` remains untouched until a reviewed live-data migration.

## Error handling

`normalizeApiError` preserves API `code`, user-safe `message`, field errors, and eligibility data. It maps 400 to validation, 404 to not-found, 409 `STALE_VERSION` separately from other 409 conflicts, 428 to precondition, 500 to server, absent responses to network, and `ECONNABORTED` to timeout. Unexpected payloads receive a safe generic message; raw diagnostics are not shown.

## Versioning

`expectedVersionHeaders(record)` emits `X-Expected-Version: id:version` only for the thirteen documented conditional mutations: crew update/activity; aircraft update/activity; flight update/status/cancellation; held-qualification expiry/revoke; assignment cancellation/reassignment; unavailability update/remove. The loaded target resource remains the source of its original token. Qualification mutation paths use the held qualification response's `qualificationId` path value but its own held-record `id:version` token. Create, eligibility, reference reads, and all other reads send no version header.

## Connectivity

There is no health endpoint in OpenAPI. `checkBackendConnectivity()` uses the existing `GET /airports` endpoint once per session and returns a normalized unavailable state. It demonstrates API reachability only; it cannot certify PostgreSQL or backend health.

## Phase 3.10 handoff and limitations

Phase 3.10 can import a service into one reviewed read workflow at a time, retain demo fallbacks, normalize errors at the UI edge, and preserve original resource versions through edit confirmation. Do not silently refresh after `STALE_VERSION`; reload and review. No services are called in Phase 3.9, no authentication/proxy exists, and backend business/eligibility logic remains server-owned.

## Verification

Run from `frontend/`: `npm run lint`, `npm run test`, and `npm run build`; from root run `git diff --check`. Exact results are included in the Phase 3.9 completion report.
