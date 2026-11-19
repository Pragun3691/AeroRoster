# Phase 3.10 - Real frontend/backend integration

## Scope and status

**IN PROGRESS - owner-authorized work-in-progress Git checkpoint.** This handoff is not full end-to-end acceptance.

Phase 3.10 connects the existing React routes to the frozen Spring Boot API through the Phase 3.9 typed Axios services. The supported runtime remains the packaged Spring Boot application with relative `/api/v1` requests. No Vite server or proxy is used for integration acceptance.

Implementation is present for all eight requested module groups. Final packaged-runtime verification is recorded below. Browser acceptance for the new integration remains pending because the browser access host is unavailable. The owner's Phase 3.10A browser PASS concerns the preceding deployment milestone and is not reused as evidence of new live CRUD/browser behavior.

The initial branch was `feature/frontend-dashboard`, tracking `origin/feature/frontend-dashboard`, at `bc3bf2b`. The only initial working-tree modification was `docs/22_PHASE310A_SAME_ORIGIN_DEPLOYMENT.md`; it has been preserved without modification during Phase 3.10 and is included in this checkpoint. The owner subsequently authorized committing and pushing the reviewed work in progress to that feature branch. No branch switch, merge or force-push is authorized or needed.

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

## Verification evidence - 19 November 2026

Modules were integrated and tested in order: reference data (4 tests), crew (12), aircraft (6), flights (7), qualifications (7), assignments (8), unavailability (7), then dashboard/table (4). These intermediate module runs passed before proceeding. Existing demo-behavior tests were adapted to the real API boundary; the original foundation and table suites were retained and expanded where needed.

| Check | Observed result |
| --- | --- |
| Complete frontend lint | PASS, zero warnings |
| Complete frontend unit suite | PASS, 11 files / 171 tests |
| Frontend production build | PASS, TypeScript and Vite; `/assets/index-BMSIXMoK.js` and `/assets/index-YGT_p5ej.css` |
| Cross-module boundaries | Included in the 171 total: 111 checks covering loading/empty/error paths, write errors, duplicate prevention and creation flows |
| Protected service headers | All thirteen protected operations checked against the original resource token; included in the five foundation tests |
| Root `mvn clean test` attempt | Stopped before testing: the owner's running JAR locks `target/aeroroster-0.1.0-SNAPSHOT.jar`; the owner's process was preserved |
| Isolated `mvn clean test` | PASS, 190 tests, zero failures/errors/skips, including migration/schema and frozen-contract checks, against aeroroster_test |
| Final isolated `mvn package` | PASS on the disposable aeroroster_test database at loopback port 15432: 190 tests, zero failures/errors/skips; executable Spring Boot JAR produced |
| Latest frontend assets inside the JAR | PASS: packaged index, `/assets/index-BMSIXMoK.js` and `/assets/index-YGT_p5ej.css` match the independently built frontend byte for byte; runtime HTTP serving remains pending |
| New packaged JAR / HTTP / controlled persistence checks | Pending; the new JAR has not been launched or exercised by the reusable verifier |
| Browser automation | Unavailable: browser access host unavailable; no new integrated browser pass claimed |

The isolated source copy is under `target/phase310-build` and uses the unchanged Maven build (npm ci, production build, Java tests, executable Spring Boot packaging). It avoids stopping or replacing the owner's running JAR. The final executable is `target/phase310-build/target/aeroroster-0.1.0-SNAPSHOT.jar`; the successful package log is `target/phase310-build/phase310-package-final-bounded.log` (BUILD SUCCESS, 19 November 2026, 17:49:53 IST). Generated build copies, logs, node_modules and JARs remain untracked/ignored. Local test credentials are never copied into the frontend or report. All write verification is restricted to `aeroroster_test`; no automated test writes target `aeroroster_dev`.

The locally generated JAR's SHA256 is `9549d7509f06c824b3d271eab58fafe9dbaf6e0737dea7292bc931d55eacb0fd`. The checkpoint review reconfirmed the final Surefire XML totals (190/0/0/0), preserved Phase 3.10A content, no backend diff, clean frontend lint and verifier syntax. The 33 candidate project files were reviewed for credentials, private keys, generated files and owner-specific paths; the README's existing password example is only the explicit `YOUR_LOCAL_PASSWORD` placeholder.

An earlier concurrent frontend rerun timed out in one test (170 passed / 1 timeout); the complete rerun with one worker passed all 171 unchanged assertions. An isolated package attempt failed when the shared PostgreSQL service was unavailable. A separate disposable SCRAM-authenticated PostgreSQL instance was then created on loopback port 15432 without modifying the shared service configuration or development database.

The subsequent packaging blocker was native memory exhaustion before Surefire ran any tests: `Native memory allocation (malloc) failed to allocate 35920 bytes`, `Chunk::new`. **This blocker was resolved for the successful final package**, using process-local `MAVEN_OPTS=-Xmx256m -XX:MaxMetaspaceSize=128m -XX:ActiveProcessorCount=2`, `JDK_JAVA_OPTIONS=-Xmx512m -XX:MaxMetaspaceSize=256m -XX:ActiveProcessorCount=2`, and `NODE_OPTIONS=--max-old-space-size=384`. No application/test assertions or POM settings were weakened. A preliminary Windows batch quoting error was corrected by moving the test JVM limits into the environment. No Maven clean operation was run while the disposable cluster was active.

`scripts/verify_phase310_package.py` is the additional packaged HTTP verifier, syntax-checked but not yet executed end to end. Before any writes it checks the SQL database name and the owned process's exact JAR, explicit test JDBC URL, port 18081 and loopback address. It preserves original tokens explicitly, checks stale rejection and unchanged resource state for all thirteen protected operations, creates uniquely tagged disposable resources, verifies them through HTTP and SQL, and removes only its own returned fixture IDs. It never targets port 8080. These are intended checks, not claimed results. It currently uses Windows process inspection; `psql` must be on PATH or supplied through `PSQL_EXE`. No owner-specific installation path is embedded.

## Checkpoint handoff and next verification steps

The frontend unit tests confirm all thirteen service header constructions, error classification, original-token retention and blocked stale retries. The final 190-test Maven package also passed existing backend concurrency/security/migration checks. **The new packaged HTTP/persistence verifier has not run**, so no fresh live CRUD, 13-operation HTTP stale matrix, browser mutation or browser-refresh pass is claimed.

1. Keep the owner's existing JAR and all PostgreSQL processes untouched while reviewing this checkpoint. Do not run root `mvn clean`: temporary database files reside under ignored `target/`, outside the isolated Maven build's own output directory.
2. Before resuming local runtime verification, inspect the existing successful package log and confirm the isolated test database is available. Launch only the isolated JAR on loopback port 18081 with an explicit test JDBC URL and privately supplied DB_* credentials. Record its PID; verify it is a separate process from the owner's JAR.
3. Set `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD`, `PHASE310_JAR` and `PHASE310_PID` privately for the verifier. Use only a loopback `aeroroster_test` URL. Ensure `frontend/dist` contains the independently built latest assets. Run `python scripts/verify_phase310_package.py` and record its actual results or exact failure.
4. Complete the manual browser checks below before closing Phase 3.10. Only after verification, clean up disposable resources/processes deliberately; do not stop or alter the owner's JAR or shared PostgreSQL service.

Git ignores `/target/`, frontend `node_modules/`, `dist/`, `.env` secrets, logs and TypeScript build metadata. The disposable credentials/state, temporary PostgreSQL clusters, crash logs, generated runners and packaged artifacts are local ignored files and are excluded from this checkpoint. No PostgreSQL password or local database export belongs in the commit. The feature branch checkpoint preserves the full source/tests/report and Phase 3.10A evidence for teammate review.

## Starting the application and pending browser acceptance

Normal owner workflow, after deliberately stopping the owner's old build when ready:

```powershell
mvn package
java -jar target/aeroroster-0.1.0-SNAPSHOT.jar
```

Use the existing private DB_* environment settings, keep loopback binding, and open `http://127.0.0.1:8080`. No Vite server is required. For controlled write acceptance, run a separate JAR against the approved test database with TEST_DB_* credentials supplied privately. Do not use development records for write tests.

With the owner's existing port-8080 process preserved, manual acceptance can instead use the isolated test JAR at `http://127.0.0.1:18081`, with its API served from that same origin. Do not submit browser write tests unless that process has been verified to use `aeroroster_test`.

Pending manual checks for the new integration:

1. Confirm dashboard, all six management pages, styling and navigation render from the latest test JAR. Directly open and refresh a real crew/aircraft/flight/assignment/held-qualification/unavailability detail URL; use IDs actually returned by the API, not assumed seed IDs.
2. Confirm real API records, loading/empty/error states, reference options and UTC values appear. Check that no demo records appear when the backend is unavailable.
3. Using clearly disposable test records, create/edit crew, aircraft and flights; grant/change/clear expiry; create/update/delete unavailability; preview/assign/cancel/reassign; and exercise permitted status changes. Confirm each supported change survives a browser refresh. Review backend rejection reasons and coverage updates.
4. Open the same editable test record in two tabs. Save a material change in one; submit the other tab's original draft. Confirm `STALE_VERSION`, preserved input, no automatic retry, and explicit discard/reload/review before starting a new edit.
5. Check the actual fingerprinted JS/CSS requests and Console in DevTools if available, recording only what is observed. The HTTP verifier's MIME/status checks are not a substitute for browser execution evidence. Record browser name/version, tested URLs, results and any failures.

Browser rendering, real browser-to-API mutations and persistence after browser refresh remain pending. HTTP persistence checks and mocked frontend tests establish separate layers; they are not a browser end-to-end pass.

## Files changed

- New shared implementation: `frontend/src/api/live.ts`, `frontend/src/api/useRemote.ts`, `frontend/src/components/common/LiveData.tsx`, `frontend/src/components/common/ReferenceData.tsx`.
- Updated configuration/contracts: `frontend/src/api/config.ts`, `frontend/src/types/api.ts`.
- Updated seven routed pages and corresponding tests: DashboardPage, CrewManagementPage, AircraftManagementPage, FlightManagementPage, QualificationsManagementPage, AssignmentsManagementPage, UnavailabilityManagementPage.
- Updated presentation: Sidebar, FlightOperationsTable, OperationalAlerts and OperationalCharts.
- Tests: expanded `frontend/src/api/apiFoundation.test.ts`; new `frontend/src/api/live.test.tsx`, `frontend/src/test/liveApi.ts`, `frontend/src/test/liveIntegration.test.tsx`.
- New packaged verifier: `scripts/verify_phase310_package.py`.
- Documentation: this report and the necessary README/status links. The pre-existing uncommitted Phase 3.10A report remains preserved.

## Limitations and closure

All operational datasets are loaded through complete paged reads and scoped child lists, suitable for the current small local application. Cross-resource reads are not an atomic roster snapshot, and global child registers/dashboard aggregation can issue many requests on a larger dataset. Java remains responsible for transaction safety. No bulk/reporting API or competing client scheduling algorithm was added.

**Phase 3.10 remains IN PROGRESS.** Frontend implementation, automated tests and executable packaging are verified to the extent above. Packaged runtime HTTP/persistence checks and integrated manual browser acceptance remain outstanding. This checkpoint does not authorize a merge or Phase 3.11 work.
