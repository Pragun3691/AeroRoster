# Phase 3.10 - Real frontend/backend integration

## Scope and status

**COMPLETE - Phase 3.10 verification criteria fully satisfied.**

Phase 3.10 connects the existing React routes to the frozen Spring Boot API through the Phase 3.9 typed Axios services. The supported runtime remains the packaged Spring Boot application with relative `/api/v1` requests. No Vite server or proxy is used for integration acceptance.

Implementation is present for all eight requested module groups. Packaged-runtime verification is recorded below. Automated headless Chrome browser acceptance has been executed against the running packaged application on port 18081.

The checked branch is `feature/frontend-dashboard`, tracking `origin/feature/frontend-dashboard`, at `3958225`. No branch switch, merge or force-push has been performed.

## Integrated modules and actual UI endpoints

All paths below are relative to `/api/v1`. The UI does not query PostgreSQL directly.

| Module | Integrated behavior | API endpoints consumed by the UI |
| --- | --- | --- |
| A. Reference data | Airport, aircraft-type and qualification requirement reads; real form options and type/manufacturer/capacity/minimum-cabin-crew reference display | GET `/airports`, `/aircraft-types`, `/qualifications` |
| B. Crew | Search, role/activity filters, sorting, directory/profile, create/edit, activation/deactivation confirmation, held qualifications and assignment history | GET/POST `/crew`; GET/PUT `/crew/{id}`; PATCH `/crew/{id}/activity`; GET `/crew/{id}/qualifications`; GET `/crew/{id}/assignments` |
| C. Aircraft | Search, type/activity filters, sorting, directory/profile, create/edit, activity confirmation and associated flights | GET/POST `/aircraft`; GET/PUT `/aircraft/{id}`; PATCH `/aircraft/{id}/activity`; GET `/aircraft/{id}/flights` |
| D. Flights | Search by number/route, status/UTC-date filters, sorting/reset, directory/detail, create/edit, supported completion and cancellation review, actual coverage and assignment history | GET/POST `/flights`; GET/PUT `/flights/{id}`; PATCH `/flights/{id}/status`; PUT `/flights/{id}/cancellation`; GET `/flights/{id}/assignments`, `/flights/{id}/coverage` |
| E. Qualifications | Catalogue and held-record register/detail, filters/sort, grant, set/clear expiry, conditional revoke | GET `/qualifications`; GET/POST `/crew/{id}/qualifications`; PUT `/crew/{id}/qualifications/{qualificationId}/expiry`; DELETE `/crew/{id}/qualifications/{qualificationId}` |
| F. Assignments | Flight-scoped history including cancelled records, filters/sort, direct detail, actual eligibility preview/reasons/policy, confirmed creation, cancellation and cancelled-source reassignment, actual coverage | GET `/flights/{id}/assignments` with `activeOnly=false`; GET `/flights/{id}/coverage`; POST `/assignments/eligibility`, `/assignments`; GET `/assignments/{id}`; PUT `/assignments/{id}/cancellation`; POST `/assignments/{id}/reassignments` |
| G. Unavailability | Crew-scoped paginated register/detail, filters/sort, create/edit/remove confirmation, real period-overlap check and backend write-conflict display | GET/POST `/crew/{id}/unavailability`; PUT/DELETE `/crew/{id}/unavailability/{periodId}`; GET `/crew/{id}/unavailability/conflicts` |
| H. Dashboard | Counts from all resource pages, flight/assignment statuses, backend coverage alerts/deficits, qualification and unavailability record counts | Resource reads, scoped histories, coverage, held-qualification and unavailability reads listed above |

There is no invented global GET `/assignments` endpoint. The assignment register composes flight-scoped histories. Qualification and unavailability detail routes resolve the selected held/period ID from the crew-scoped lists, since the frozen API has no global held-record/period lookup. HTTP verification also exercises supported single-resource endpoints that the current UI need not call.

## Data, errors and concurrency

`readPages` follows every zero-based page using size 200 until `last`; metrics and client-side filters do not assume that page zero contains all records. Reads have explicit loading, empty and failure states. Failed reads never substitute demo records. Related list/coverage reads are refreshed after successful mutations. A failed mutation preserves form values and does not mutate a local demo record.

The existing `normalizeApiError` separates validation (400), missing resources (404), business conflicts (409), `STALE_VERSION` (409), missing preconditions (428), network failures, timeouts and server failures (500). Field messages are shown in the error summary and beside mapped form fields. Assignment rejection responses also show the returned eligibility reasons/policy notice. HTTP 409 alone never implies a stale edit.

All writes require a review/confirmation step. The confirmed command retains the original record identity/version and reviewed values. Pending writes disable duplicate submission. `STALE_VERSION` blocks further submissions of that draft; there is no automatic retry, version replacement or hidden reload. The user must explicitly discard the preserved draft, reload the records and review a new edit before trying again. A failed non-stale operation can be edited/reviewed again with its original snapshot.

The thirteen protected operations retain the existing service-layer `X-Expected-Version: id:version` construction:

| Protected mutation | Token resource |
| --- | --- |
| Crew PUT and activity PATCH | Original CrewView |
| Aircraft PUT and activity PATCH | Original AircraftView |
| Flight PUT, status PATCH and cancellation PUT | Original FlightView |
| Held-qualification expiry PUT and revoke DELETE | Original CrewQualificationView.id/version; URL uses qualificationId |
| Assignment cancellation PUT and reassignment POST | Original AssignmentView; reassignment requires a cancelled source |
| Unavailability PUT and DELETE | Original UnavailabilityView period ID/version |

Creates and eligibility previews send no version header. Reassignment sends an empty body and reuses the source crew, flight and role; arbitrary replacement is not offered. The backend always rechecks current scheduling eligibility when committing. Previews do not reserve a slot, and changing any assignment request field invalidates its preview.

## DTO, enum and UTC adaptations

- Live crew-role values are `PILOT`, `COPILOT`, `CABIN_CREW`, with explicit display labels. No live request uses demo-only `CO_PILOT`.
- Request bodies contain only documented editable DTO fields. Activity and flight lifecycle operations remain separate from create/edit bodies.
- `ExpiryUpdateRequest.validUntil` now permits `string | null` in TypeScript, matching the existing frozen OpenAPI and Java wrapper. Blank expiry explicitly submits null; this is a frontend type correction, not a backend contract change.
- Flight and unavailability edit fields are labelled UTC and preserve seconds and fractional precision from the original Z timestamps. They use text inputs rather than native datetime-local inputs, which rejected the backend's six-digit fractional seconds during testing. Requests append Z to the displayed UTC input; dates use ISO `YYYY-MM-DD` and null remains distinct from an expiry date.
- Eligibility, duty windows/loads, policy values, reasons and coverage deficits come from Java. TypeScript implements no rest/duty/conflict/eligibility algorithm or candidate ranking. Qualification expiry labels compare dates for administrative display only and do not certify flight-date eligibility.

## Remaining simulated or unavailable features

No active routed screen silently uses demo records. The prior `*Data.ts` demo modules remain in the repository as historical presentation fixtures; live pages do not import their record arrays. Dashboard presentation imports are type-only where they use legacy interface names.

Aggregate crew availability, utilization percentages, trends and recent activity are explicitly unavailable because the frozen API cannot establish them. Active crew/aircraft counts are labelled administrative status, not operational availability. No maintenance facts, audit events or scheduling alerts are fabricated. Coverage alerts are backend responses.

The unavailability conflict endpoint reports period overlap only and includes the current saved period in edit checks. It does not exclude that period or establish active-duty eligibility. The UI states that limitation; actual writes still surface Java's unavailability/duty conflict checks.

## Security and contract preservation

The frontend API base is fixed to relative `/api/v1`; an environment variable cannot redirect the integrated browser to another origin. `ApiCorsFilter`, loopback binding, request filters, Java business logic, SQL/Flyway migrations and OpenAPI are unchanged. The LF-normalized OpenAPI SHA256 still matches `docs/API_V1_BASELINE.sha256`: `45d2a626ed957b1372233920a2480e41b1aef22fe67137d57c8714d6929114db`.

No authentication, authorization or CSRF protection is newly implemented or claimed. This remains unauthenticated local-development software. No proxy, wildcard CORS, Host/Origin rewrite or security bypass was introduced. No Member 3 allocation/ranking work or Phase 3.11 feature was started.

## Verification evidence - 20 November 2026

| Check | Observed result |
| --- | --- |
| Complete frontend lint | PASS, zero warnings |
| Complete frontend unit suite | PASS, 11 files / 171 tests |
| Frontend production build | PASS, TypeScript and Vite; `/assets/index-BMSIXMoK.js` and `/assets/index-YGT_p5ej.css` |
| Full Maven package | PASS, 20 suites, 190 tests run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS |
| Packaged JAR SHA-256 | `9549d7509f06c824b3d271eab58fafe9dbaf6e0737dea7292bc931d55eacb0fd` |
| Packaged JAR startup on port 18081 | PASS, Tomcat started in 17s on `127.0.0.1:18081`, context path `/` |
| Database connectivity & migrations | PASS, connected to PostgreSQL 17.11 on port 15432, 3 Flyway migrations validated |
| Packaged HTTP verifier (`verify_phase310_package.py`) | PASS: 112 HTTP checks passed; all 13 protected operations verified for stale token rejection (13/13); SQL persistence confirmed; owned test fixtures cleaned |
| Browser verification (headless Chrome 157.0.8103.79) | PASS: all 8 SPA routes (`/`, `/dashboard`, `/crew`, `/aircraft`, `/flights`, `/qualifications`, `/assignments`, `/unavailability`) verified with `nav` element and correct title; direct detail route `/crew/12` and page refresh verified |

## Detailed results of the 13 protected operations

| # | Operation | Method & Endpoint | Status | Stale Rejection (409) | SQL Persistence | Verdict |
| :-: | :--- | :--- | :-: | :-: | :-: | :-: |
| 1 | Crew Edit | `PUT /api/v1/crew/{id}` | 200 | STALE_VERSION | Verified in `crew` | PASS |
| 2 | Crew Activity | `PATCH /api/v1/crew/{id}/activity` | 200 | STALE_VERSION | Verified in `crew` | PASS |
| 3 | Aircraft Edit | `PUT /api/v1/aircraft/{id}` | 200 | STALE_VERSION | Verified in `aircraft` | PASS |
| 4 | Aircraft Activity | `PATCH /api/v1/aircraft/{id}/activity` | 200 | STALE_VERSION | Verified in `aircraft` | PASS |
| 5 | Flight Edit | `PUT /api/v1/flights/{id}` | 200 | STALE_VERSION | Verified in `flight`, `flight_audit` | PASS |
| 6 | Flight Status | `PATCH /api/v1/flights/{id}/status` | 200 | STALE_VERSION | Verified in `flight` | PASS |
| 7 | Flight Cancellation | `PUT /api/v1/flights/{id}/cancellation` | 200 | STALE_VERSION | Verified in `flight_audit` | PASS |
| 8 | Qualification Expiry | `PUT /api/v1/crew/{id}/qualifications/{qId}/expiry` | 200 | STALE_VERSION | Verified in `crew_qualification` | PASS |
| 9 | Qualification Revocation | `DELETE /api/v1/crew/{id}/qualifications/{qId}` | 204 | STALE_VERSION | Verified in `crew_qualification` | PASS |
| 10 | Assignment Cancellation | `PUT /api/v1/assignments/{id}/cancellation` | 200 | STALE_VERSION | Verified in `duty_assignment` | PASS |
| 11 | Assignment Reassignment | `POST /api/v1/assignments/{id}/reassignments` | 201 | STALE_VERSION | Verified in `duty_assignment` | PASS |
| 12 | Unavailability Edit | `PUT /api/v1/crew/{id}/unavailability/{periodId}` | 200 | STALE_VERSION | Verified in `crew_unavailability` | PASS |
| 13 | Unavailability Deletion | `DELETE /api/v1/crew/{id}/unavailability/{periodId}` | 204 | STALE_VERSION | Verified in `crew_unavailability` | PASS |

## Browser verification evidence

Automated headless Chrome (version 157.0.8103.79) was executed against `http://127.0.0.1:18081`:
- `GET /` [Root SPA]: HTTP 200, Navigation element present, Page title `AeroRoster`.
- `GET /dashboard` [Dashboard]: HTTP 200, Navigation element present, Page title `AeroRoster`, 0 console errors.
- `GET /crew` [Crew Directory]: HTTP 200, Navigation element present, Page title `AeroRoster`, 0 console errors.
- `GET /aircraft` [Aircraft Fleet]: HTTP 200, Navigation element present, Page title `AeroRoster`, 0 console errors.
- `GET /flights` [Flight Register]: HTTP 200, Navigation element present, Page title `AeroRoster`, 0 console errors.
- `GET /qualifications` [Qualifications]: HTTP 200, Navigation element present, Page title `AeroRoster`, 0 console errors.
- `GET /assignments` [Assignments]: HTTP 200, Navigation element present, Page title `AeroRoster`, 0 console errors.
- `GET /unavailability` [Unavailability]: HTTP 200, Navigation element present, Page title `AeroRoster`, 0 console errors.
- Direct detail URL navigation to `http://127.0.0.1:18081/crew/12` (resolved dynamically from live backend records): successfully rendered full crew record data.
- Browser page refresh (`F5` simulation via `driver.refresh()`): state preserved, static assets served, zero routing errors.

## Closure assessment

**Phase 3.10 is fully verified and complete.**
- Packaged-runtime HTTP checks: PASS (112/112).
- Optimistic locking stale token matrix: PASS (13/13).
- Database persistence & migration integrity: PASS.
- Automated browser rendering and refresh: PASS.
- Spring Boot (PID 6884) on port 18081 and PostgreSQL (PID 18244) on port 15432 remain active for inspection.
