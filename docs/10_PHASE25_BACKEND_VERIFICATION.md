# Phase 2.5 backend integration and reliability verification

Historical Phase 2.5 evidence. The owner subsequently authorized Phase 2.6 versioned edits and the team handoff. Current contracts, migration V3 and closure of the stale administrative edit limitation are documented in [the Phase 2.6 handoff](11_PHASE26_TEAM_HANDOFF.md).

## Checkpoint and risk-based plan

The audit began on feature/phase2-core at commit 87439d2 with a clean working tree. Phases 2.1 through 2.4 are committed. Review the actual migrations, entities, repositories, services, DTOs, controllers, exception/JSON/origin handling, scheduling calculations, OpenAPI and tests before changing production behavior.

Prioritize integrated HTTP workflows and persisted state, assignment races against flight/crew/qualification/unavailability edits, exact rolling-window/rest boundaries, strict API input handling and local-development exposure. Reproduce any defect in a regression test before applying a focused fix. Preserve existing service/API contracts and applied migrations unless evidence requires a documented correction.

Capture read-only development-table signatures before verification and compare them afterward. Run all write/destructive verification against guarded aeroroster_test with rollback or exact ownership cleanup. Rehearse migrations and Hibernate validation in a new isolated empty schema within that database, with a scoped connection so auditing cannot affect public seed rows. Remove only the schema created by that test.

Add real-HTTP operational/lifecycle workflows, fill only high-risk coverage gaps, and run deterministic row-lock races with independent connections. Compare all 42 endpoints and JSON/OpenAPI contracts. Verify local-only host/origin handling, strict DTO binding and sanitized errors, and scan tracked source/docs plus verification logs without displaying possible credentials.

Run mvn clean test, targeted reliability/concurrency checks, mvn package and git diff --check. Start the packaged JAR for representative read/write HTTP verification against the dedicated test database, and verify loopback listeners, homepage, CSS, documentation, migrations and Hibernate. Inspect installed JDK locations and use Java 21 if available, without installing software. Record exact results, limitations and Member 2/3 readiness.

## Repository and architecture audit

Reviewed AGENTS.md, README and docs/01 through docs/09, V1/V2 SQL, ten entities and repositories, all services and domain records, seven REST controllers and their transport DTOs, exception handling, JSON/origin configuration, static OpenAPI, test annotations and existing tests. The architecture keeps scheduling decisions in Java; PostgreSQL supplies integrity constraints, transactional row locking and the existing flight audit trigger. No entity is returned directly by the API. No runtime dependency, schema migration or architecture change is introduced in this phase.

Crew, flight and aircraft parent locks precede child locks. Aircraft moves lock both old/new aircraft in ascending ID order. Writes require writable READ_COMMITTED transactions, and refresh locked entities to avoid stale first-level-cache decisions. Eligibility previews are advisory; assignment repeats checks inside its transaction. Direct database writers can bypass service scheduling rules and are outside the supported application contract.

## Verification matrix

| Area | Evidence and expected outcome |
| --- | --- |
| A: normal operations | BackendWorkflowHttpTest retrieves catalogs, creates owned aircraft and all four required crew, grants role/type qualifications, creates a flight, previews eligibility and staffs it. Coverage and paged rosters/history agree; SQL joins verify every crew/flight/aircraft/qualification relationship and buffered duty snapshot. |
| B: invalid administration | Real HTTP rejects duplicate license/registration, missing airport, aircraft overlap, unsafe aircraft/crew deactivation, assigned-flight edits/cancellation, early completion, qualification revocation/shortening. Final persisted flight time and assignment count remain correct. Existing core/persistence tests cover other invalid references, uniqueness and history restrictions. |
| B: invalid assignments | Real HTTP rejects role mismatch, absent/expired qualification, duplicate active pair and extra pilot/copilot. Expiry exactly on the UTC departure date succeeds. |
| B: temporal rules | Real HTTP rejects overlapping duties, zero rest at half-open adjacency, 12 hours in rolling 24 hours and a sixth eight-hour duty within seven days. A midnight-spanning unavailable period rejects assignment; deleting that owned period permits it with correct UTC buffers. |
| C: assignment lifecycle | Cancellation is idempotent, cancelled rows remain, reassignment creates a new ID, active/history queries differ correctly, coverage reflects the vacancy, duplicate active assignment fails, cancelled-flight reassignment fails. SQL confirms both cancelled history rows. |
| D: flight lifecycle | A fully staffed past flight completes; reopening, cancelling and editing a completed flight fail. Future empty cancellation is idempotent. Audit actions are exactly INSERT,UPDATE for each successful lifecycle and rejected changes add no audit entries. |
| Scheduling arithmetic | DutyRulesTest, DutyWindowTest, SchedulingPolicyTest: microsecond boundaries, one-hour/30-minute buffers, half-open overlap, exact ten-hour rest in both directions, clipped rolling windows, midnight and UTC-day boundaries; deterministic seeded calculator oracle retained. New independent UTC-day test raises rolling caps to exercise exactly 14 hours and one microsecond above. |
| Concurrency | AssignmentConcurrencyIntegrationTest, FlightConcurrencyIntegrationTest and ApiHttpConcurrencyTest use independent committed PostgreSQL transactions and observed row-lock waits. Verify final persisted state for crew double booking, last pilot slot, deactivation, expiry/revocation, unavailability, cancellation, aircraft operations and the new reschedule/assignment race. |
| Rollback/isolation | A flushed assignment rolls back with its enclosing operation; read-only and REPEATABLE_READ outer write transactions are rejected. Stale cached qualification/flight/aircraft decisions are refreshed or read with scalar projections. |
| REST contracts | OpenApiContractTest compares all 42 controller method/path pairs, record fields, four enum families and schema references. Existing API tests cover methods/status codes/Location, pagination/filter validation, required fields, media types, missing resources, malformed JSON, strict timestamps, UTC serialization and sanitized errors. |
| Local security | Regression tests reproduce and close foreign-host same-origin bypass and duplicate JSON key ambiguity. Allowed loopback names stay usable. Existing cross-origin, managed-field injection, enum/coercion and error-sanitization coverage remains. |
| Fresh migration | FreshMigrationVerificationTest creates a unique empty schema only inside aeroroster_test, applies and validates V1/V2, checks all seed counts and ten FKs, validates all ten Hibernate entities, reads mapped relationships/enums/instants and rolls back an audited update. Public-table counts remain unchanged. |
| Development preservation | Compare before/after row counts plus ordered content signatures for all ten application tables and Flyway history in aeroroster_dev. No development writes are used for verification. |
| Packaged runtime | scripts/verify_phase25_package.py verifies an actual JAR listener with real read/write requests, errors, local host/origin rules, cancellation/reassignment/history and SQL audit evidence. Owned fixture rows are removed afterward. |

## Scheduling correctness findings

Limits are configurable illustrative academic rules, not certified aviation regulations. Defaults remain one-hour preflight and 30-minute postflight buffers, ten hours minimum rest, ten duty hours per rolling 24 hours, 40 per rolling seven days and 14 per UTC calendar day. Limits reject strictly greater values, preserving exact-boundary acceptance. Assignments store derived duty instants, not just flight duration.

Rolling calculations sum exact Duration intersections. Load is piecewise linear; candidate window starts at duty start/end and those instants minus the rolling period contain its extrema. UTC-day evaluation clips at UTC midnight. Relevant history includes ACTIVE duties on completed flights; cancelled assignments do not consume duty load. Non-overlapping adjacency has zero rest, so it passes the overlap check but fails the ten-hour rest check. Qualification validity is inclusive on UTC flight departure date, as established in Phase 2.3.

The 14-hour UTC-day cap is redundant under the default ten-hour rolling-24-hour cap: a UTC calendar day is itself one such 24-hour window. It is not contradictory. Keep both configurable requirements. The new independent-cap test demonstrates 14 hours accepted, 14 hours plus one microsecond rejected only by UTC_DAILY_DUTY_LIMIT, and a 14.5-hour midnight-crossing duty accepted when neither UTC day exceeds 14 hours and rolling caps are raised.

The new reschedule race holds the flight parent lock until both worker transactions are observed waiting. If assignment commits first, the edit rejects and original duty times remain; if the edit commits first, assignment succeeds using the new schedule. The test checks persisted schedule and duty instants, rather than only counting successful responses. Existing races protect safety state without promising optimistic edit conflict detection for arbitrary administrative PUTs.

## Defects reproduced and fixed

1. **Foreign-host bypass of local browser policy.** A request using Host rebind.example and matching Origin was treated as same-origin and returned 200. A browser GET without Origin also returned 200. Both reproductions expected 403 and failed before the fix. ApiCorsFilter now checks a literal loopback hostname allowlist before checking Origin, without DNS resolution. Foreign-host requests return the shared HOST_NOT_ALLOWED error. This closes a DNS rebinding route into a loopback listener; it does not authenticate direct clients.
2. **Duplicate JSON properties silently changed the requested role.** Posting COPILOT followed by PILOT under the same role key returned 201. The failing reproduction expected 400. Jackson stream duplicate detection now rejects the ambiguity as MALFORMED_JSON before a service write. Existing strict DTO coercion and unknown-field rules remain.
3. **Oversized pagination offset returned 500.** page=2147483647 with size=200 passed HTTP validation but exceeded JPA's integer offset range. The reproduction failed with INTERNAL_ERROR instead of the expected 400. ApiParameters now checks the product using long arithmetic and returns INVALID_PARAMETER for page before a repository call. Offsets exactly within range remain supported.
4. **Maximum valid page incorrectly reported last=false.** page=2147483647 with size=1 returned an empty page but Spring's integer page-plus-one calculation overflowed. The reproduction failed its last-page assertion. PageResponse now computes that flag with long arithmetic. The test also checks the largest in-range offset for size=200.
5. **OpenAPI integer widths overstated the Java contract.** Java int fields such as experienceYears and page were documented as int64. The added record-width assertion failed before correcting the specification to int32 for record fields, all concrete page schemas and paging query parameters. Page parameters now describe the cross-parameter offset constraint. Paths and resource shapes remain unchanged.

The three initial security reproductions failed as expected, then passed after these focused fixes. An additional allowed-host regression covers localhost (case insensitive), 127.0.0.1 and IPv6 loopback spellings. The OpenAPI error enum/403 response and REST handoff documentation now include HOST_NOT_ALLOWED and duplicate-property rejection.

## Database integrity and migration scope

Applied V1/V2 files are unchanged. The rehearsal starts from an empty isolated namespace on PostgreSQL 17, not a reset of either existing database and not creation of a new PostgreSQL database/cluster. A single schema-scoped connection ensures the unqualified audit trigger writes into the rehearsal schema. Cleanup removes only its generated schema after restoring the connection's original schema. No existing application schema is dropped. This verifies application DDL/seed reproducibility and Hibernate mapping compatibility; database provisioning/role permissions on a separately provisioned server remain an environment setup concern.

Fresh seed counts: airport 5; aircraft_type 2; aircraft 3; crew 12; qualification 6; crew_qualification 12; flight 5; crew_unavailability 1; duty_assignment 4; flight_audit 5. Flyway versions 1 and 2 succeed and validate. Invalid foreign-key insertion fails. An ORM flight update creates an UPDATE audit row in the same transaction, and rollback removes it.

Committed HTTP/concurrency fixtures record only IDs created by their test and remove dependent rows in a cleanup transaction after workers stop. Deleting owned flight fixtures creates a DELETE audit event, which is then removed only for those same owned flight IDs. Database identity sequences can advance during tests; sequence continuity is not a data-preservation guarantee.

## Local security and deployment limitations

Default server.address is 127.0.0.1. Keep SERVER_ADDRESS on loopback; overriding it to a public interface is unsupported for this unauthenticated build. Allowed API hostnames are localhost, 127.0.0.1 and ::1. An Origin header must additionally match scheme/host/port. No permissive CORS headers are emitted. Host/origin restrictions are not authentication and do not stop direct local clients. Remote production deployment is blocked until authentication, authorization and deployment security are designed in a later milestone.

JSON rejects unknown/duplicate fields, managed fields, numeric enum ordinals, scalar coercion, missing required values and invalid timestamp shapes. Errors omit rejected values, database diagnostics and stack traces. Internal failure details remain server-side. Keep database passwords in environment variables and avoid enabling sensitive parameter logging. Credential-pattern scanning covers tracked source/docs and verification logs without printing candidate values; it is a heuristic review, not proof against every possible secret format.

## Member 2 and Member 3 handoff

| Consumer need | Current contract / limitation |
| --- | --- |
| Member 2 dashboard | Compose totals from paged resource totalElements and per-flight coverage; no aggregate dashboard endpoint. |
| Crew/aircraft/flight administration | CRUD-style reads/updates/activity/status, filters, explicit qualification resources and safe conflict errors are available. No physical crew/aircraft delete workflow is intended. |
| Manual assignments | Eligibility feedback, assignment create/cancel/reassign, paged active/history rosters and coverage are available. Treat previews as advisory and show commit-time 409 feedback. |
| Frontend errors | Stable ApiError code/message/fieldErrors and optional eligibility. POST 201 includes relative Location; DELETE 204 has no JSON body. Growing collections are PageResponse; fixed catalogs and held qualifications are arrays. |
| Member 3 candidates | Page active crew by role and check each candidate's eligibility. No bulk eligibility/candidate-ranking endpoint; ranking/allocation are future work. |
| Staffing and commits | Per-flight coverage exposes role counts/deficits; assignment performs conflict-safe transactional revalidation. No atomic multi-crew allocation endpoint. |
| Scheduling calendar | Paged flights and per-crew assignments supply inputs. Flight filters select departure instants, not every flight overlapping an arbitrary calendar interval. An efficient cross-crew overlap/calendar endpoint is a future Phase 3/4 contract gap. |
| Duty reports | ACTIVE/completed assignment snapshots and historical/cancelled rows are available per crew/flight. No global date-range duty aggregation, exports or report endpoint. Eligibility dutyLoad includes the proposed duty and is not a general historical report total. |

Member 2 can build the planned local administration/manual-assignment UI. Member 3 can start candidate selection and manual conflict-safe commits, but efficient bulk allocation, cross-crew calendars and period reports need the planned next-phase APIs. These are documented handoff gaps, not reasons to add unrelated features during this review.

Administrative full PUTs serialize at the row level but use last-committed-writer-wins semantics; there is no ETag/version precondition to detect a stale human form. The locks prevent invalid committed scheduling state, not every overwritten administrative field. Agree on optimistic edit/version contracts before collaborative editing. Flight completion does not enforce minimum staffing, matching the existing service policy. Audit history records flight action/number/time, not complete before/after schedules or assignment cancellation timestamps. Qualification links have no historical version snapshot. These established limits remain unchanged.

## Execution evidence and acceptance

Verification date: 2026-11-18. Build logs are local ignored artifacts; the excerpts below are sanitized. Commands were run from the repository root in PowerShell with the dedicated PostgreSQL test configuration. No software was installed, and no commit, push or merge was made.

### Reproductions and targeted checks

```powershell
mvn '-Dtest=LocalApiSecurityRegressionTest' test
# Before fixes: Tests run: 3, Failures: 3, Errors: 0, Skipped: 0; BUILD FAILURE
# Foreign Host+Origin: expected 403, got 200.
# Foreign Host without Origin: expected 403, got 200.
# Duplicate role key: expected 400, got 201.
# After fixes: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS

mvn '-Dtest=BackendWorkflowHttpTest,FreshMigrationVerificationTest,DutyRulesTest,AssignmentConcurrencyIntegrationTest' test
# Tests run: 33, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS

mvn '-Dtest=ApiPaginationBoundaryTest' test
# Before fixes: Tests run: 2, Failures: 2, Errors: 0, Skipped: 0; BUILD FAILURE
# Oversized offset: expected 400, got 500.
# Largest valid page: expected last=true, got false.

mvn '-Dtest=OpenApiContractTest' test
# Added width assertion before specification fix:
# Tests run: 3, Failures: 1, Errors: 0, Skipped: 0; BUILD FAILURE

mvn '-Dtest=ApiPaginationBoundaryTest,OpenApiContractTest' test
# After fixes: Tests run: 5, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS

mvn clean test
# Compiling 100 source files with javac [debug parameters release 21]
# Compiling 20 source files with javac [debug parameters release 21]
# Tests run: 179, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS; Total time: 01:44 min
```

An earlier complete clean run passed 177 tests before the additional two pagination reproductions were introduced. The final clean run includes all 165 original cases plus 14 added cases. Failed reproductions were fixed and rerun; no failures were suppressed and no tests were disabled.

### Final test inventory

| Test class | Cases |
| --- | ---: |
| DutyWindowTest | 3 |
| DutyRulesTest | 14 |
| SchedulingPolicyTest | 7 |
| PersistenceIntegrationTest | 10 |
| CoreServicesIntegrationTest | 30 |
| SchedulingIntegrationTest | 27 |
| AssignmentConcurrencyIntegrationTest | 13 |
| FlightConcurrencyIntegrationTest | 2 |
| ServiceValidationTest | 10 |
| PostgresDatabaseGuardTest | 4 |
| ApiControllerTest | 6 |
| ApiIntegrationTest | 36 |
| ApiHttpConcurrencyTest | 2 |
| OpenApiContractTest | 3 |
| BackendWorkflowHttpTest | 5 |
| FreshMigrationVerificationTest | 1 |
| LocalApiSecurityRegressionTest | 4 |
| ApiPaginationBoundaryTest | 2 |
| **Total** | **179** |

Concurrency-related classes contain 17 cases, including transaction/isolation/cache regressions as well as simultaneous races. Lock races coordinate worker startup and observe pg_stat_activity lock waits before releasing a held parent lock. The 50ms polling interval observes an actual database condition; it does not choose a winner by arbitrary sleeping. All required competing operations leave a valid final state, and no unresolved deadlock or timeout was observed.

### Changed file inventory

Production fixes: src/main/java/com/aeroroster/web/api/ApiCorsFilter.java, ApiJsonConfiguration.java, ApiParameters.java and dto/PageResponse.java. Contract: src/main/resources/static/api/v1/openapi.json.

Regression additions: src/test/java/com/aeroroster/verification/BackendWorkflowHttpTest.java, FreshMigrationVerificationTest.java, LocalApiSecurityRegressionTest.java; src/test/java/com/aeroroster/web/api/ApiPaginationBoundaryTest.java. Existing test extensions: domain/scheduling/DutyRulesTest.java, service/AssignmentConcurrencyIntegrationTest.java and web/api/OpenApiContractTest.java.

Reproducible manual check: scripts/verify_phase25_package.py. Documentation: README.md, docs/03_RULES_AND_DECISIONS.md, docs/04_PHASE_ROADMAP.md, docs/09_PHASE24_REST_API.md and this report. AGENTS.md needs no change: its existing transaction, migration, security and milestone gates remain applicable.

### Packaged application and environment

```powershell
mvn package
# Tests run: 179, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS; Total time: 01:14 min
# target/aeroroster-0.1.0-SNAPSHOT.jar produced and repackaged

# Process-scoped configuration for the verification JAR:
$env:DB_URL='jdbc:postgresql://localhost:5432/aeroroster_test'
$env:DB_USER=if ($env:TEST_DB_USER) { $env:TEST_DB_USER } else { 'postgres' }
$env:DB_PASSWORD=$env:TEST_DB_PASSWORD
$env:PORT='18081'
$env:SERVER_ADDRESS='127.0.0.1'
$env:DEBUG='false'
$jarProcess=Start-Process -FilePath (Get-Command java).Source `
  -ArgumentList @('-jar','target/aeroroster-0.1.0-SNAPSHOT.jar') `
  -WorkingDirectory (Get-Location).Path -WindowStyle Hidden -PassThru `
  -RedirectStandardOutput target/phase25-jar.log `
  -RedirectStandardError target/phase25-jar-error.log
# Readiness observed in log before HTTP checks; process ID 29896.
# Database: .../aeroroster_test (PostgreSQL 17.11)
# Successfully validated 2 migrations
# Schema "public" is up to date. No migration necessary.
# Initialized JPA EntityManagerFactory for persistence unit 'default'
# Tomcat started on port 18081 (http)
# Started AeroRosterApplication in 21.797 seconds

python scripts/verify_phase25_package.py
# Packaged JAR: 32 HTTP checks passed; persisted audit verified; owned fixtures cleaned

Get-NetTCPConnection -OwningProcess 29896 -State Listen
# LocalAddress  LocalPort  OwningProcess
# 127.0.0.1    18081      29896
Stop-Process -Id 29896
# Stopped owned verification JAR process

git diff --check
# Exit 0; no whitespace errors. Git may emit Windows LF/CRLF conversion notices.
```

The packaged checks cover homepage 200, CSS, local viewer/JavaScript, OpenAPI (42 operations and the new error code), catalogs, validation/missing-resource errors, origin rejection, foreign host with/without Origin, matching Origin, duplicate JSON keys, both pagination extremes, crew/qualification/aircraft/flight creation, preview/assignment, duplicate and unsafe-edit rejection, coverage, cancellation, reassignment, active/history queries and flight cancellation. SQL confirms INSERT,UPDATE audit actions, and cleanup checks prove all recorded fixture IDs and their dependent rows were removed. The server process was stopped after verifying its sole listener was loopback.

Build/runtime: Java 26.0.2, Maven 3.10.1 and PostgreSQL 17.11. The clean compiler explicitly reports release 21; all packaged AeroRoster application classes have bytecode major version 65. Java 21 was not found on PATH, JAVA_HOME, the standard installed-JDK directories/registry or C:\\Tools. **An actual Java 21 runtime check was not performed.** No software was installed and the compilation target remains 21.

### Preservation and security evidence

Before verification, read-only queries captured counts and ordered row-content hashes for aeroroster_dev. For each application table the query computes count plus md5(string_agg(to_jsonb(row)::text ordered by id)); Flyway history is ordered by installed_rank. No row dump or personal data export is stored. The snapshot was retained outside target so mvn clean could not remove it. The exact same queries were repeated after packaged writes and cleanup:

```text
Development preservation: all 11 table counts and ordered content signatures unchanged
Credential-pattern scan: 155 files; 0 candidates (values withheld)
```

The final heuristic scan includes tracked files, new nonignored files and verification/JAR logs. It checks private-key headers, common token/key shapes, URL-embedded credentials and literal password/secret/API-key assignments, excluding obvious placeholders/environment variable names. No candidate values were printed. Applied migration diff is empty. The development-table comparison includes both row counts and content, rather than counts alone.

### Acceptance recommendation and remaining gates

**Qualified recommendation: ready for local Phase 2 teammate integration under the documented contracts.** All 179 tests, all four integrated workflow groups, real PostgreSQL lock races, fresh-schema migration/Hibernate rehearsal, 32 packaged HTTP checks, development preservation and whitespace checks pass. All 42 operations retain their implemented contracts with corrected integer widths and documented host/pagination errors. No known critical invalid-booking, duty-calculation or transaction-safety defect remains in the supported service paths.

This is not an unconditional claim that every verification ambition is complete: Java 21 runtime portability and provisioning a distinct fresh database/server were not exercised. Administrative stale-form PUTs still lack optimistic conflict detection, so the broader requirement of preventing every silent administrative overwrite is not satisfied. If that is an acceptance requirement, keep that gate open until an owner-approved version/ETag contract is implemented. No such schema/architecture change was made during this review. Public deployment remains blocked by the intentional absence of authentication/authorization. Bulk calendar/report/allocation contracts remain planned Member 3 work.

The repository remains on feature/phase2-core at the original committed checkpoint 87439d2 with these 18 files modified/new and uncommitted. No unrelated project was changed. Proposed commit message, subject to owner approval:

```text
fix: harden local API and verify Phase 2.5 backend reliability
```

Stop after this Phase 2.5 review; no next milestone, commit, push or merge is authorized by this report.
