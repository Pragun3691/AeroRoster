# Phase 2.6 backend freeze and team handoff

## Implementation plan and checkpoint

Phase 2.5 is committed at 48237bf on feature/phase2-core; the working tree was clean before this milestone. Keep the current Java service architecture, six mutable resource types, 42 operation paths and scheduling lock order. Add version columns only through V3, JPA @Version mappings and version fields on resource DTOs. Administrative replacements, activity/status changes, deletions, cancellations and reassignment require X-Expected-Version: id:version. Compare the original client's token against refreshed state after acquiring the existing parent/child locks. Missing preconditions return 428; stale edits return 409 STALE_VERSION without side effects. Resource identity in the token prevents a revoked/regranted qualification from matching an old version-zero token.

Existing trusted Java command overloads remain available for internal operations; browser/user edits must use conditional overloads. New resources, eligibility and reads need no precondition. Versions are per row; scheduling invariants still recheck related records under existing locks. No-op writes retain their version, and even a cancellation retry must supply a current token. JPA optimistic locking additionally protects stale entity writes that do not go through the lock helpers.

Risks: client compatibility, missing a mutation path, stale child delete/recreate tokens, refresh/optimistic lock interaction, version changes before response mapping, migration initialization of existing rows and lock-order regressions. Update all controllers/DTO mappings, OpenAPI, HTTP fixtures and scripts together. Add PostgreSQL two-client and deterministic competing-update tests, stale cancellation/delete tests, direct JPA optimistic-lock tests, fresh V1/V2/V3 rehearsal and contract checks. Run targeted checks, mvn clean test, mvn package, packaged HTTP and git diff --check. Preserve development table content using before/after signatures; all verification writes use aeroroster_test.

## Architecture

| Layer | Responsibility and boundaries |
| --- | --- |
| Spring MVC controllers | Bind and validate HTTP syntax, DTOs, paging and conditional headers. Return immutable resources or ApiError; never expose entities. |
| Java services | Own business validation, transactions, scheduling checks and version comparisons. ServiceTransactions uses a separate proxied ServiceUnitOfWork for writable READ_COMMITTED operations and read-only queries. |
| Repositories/entities | Read/write JPA mappings and query projections. Six mutable entities have @Version. Repository saves alone do not enforce scheduling rules. |
| PostgreSQL/Flyway | Own PK/FK/check/uniqueness constraints, indexes, versioned migrations and transactional flight auditing. Hibernate validates; it does not generate schemas. |
| Scheduling domain | SchedulingPolicy derives duty snapshots; DutyLoadCalculator clips exact intervals; DutyRules checks overlap/rest/load; CrewEligibilityService combines them with current database state. |

Airport supplies source/destination references to Flight. AircraftType describes Aircraft and role-specific Qualification requirements. CrewQualification joins Crew to Qualification with optional expiry. CrewUnavailability belongs to Crew. DutyAssignment joins Crew and Flight with role, status and immutable-at-creation duty-time snapshots. FlightAudit retains scalar flight identifiers/action/number/time; it is not a complete event-sourcing history. There are ten tables plus Flyway history. Full relationship details are in [the persistence guide](06_PHASE21_PERSISTENCE.md).

Assignment flow: validate the request, lock crew then flight then aircraft, refresh current state, evaluate eligibility, derive the duty window, insert and flush, commit, return a detached DTO. Cancellation retains the row. Reassignment validates a cancelled source and creates a new assignment ID with current eligibility and buffers. Parent rows are acquired in ascending ID order within each type; child locks follow parents. A flight move locks old/new aircraft in ascending order. Version checks run after the appropriate lock/refresh and before changing state.

The Phase 2.6 lock helper loads an entity without a locking-query version comparison, then refreshes it with PESSIMISTIC_WRITE. The refresh itself acquires the row lock and hydrates the latest version. This preserves existing outer-persistence-context safety when a previously cached aircraft/crew/flight was changed concurrently. The original stale-cache regression remains in the suite.

## Fresh-machine setup

Use a JDK 21, Maven 3.9+ and PostgreSQL 17, with git and psql available on PATH. Verified locally with Maven 3.10.1 and PostgreSQL 17.11. The final suite passed on Java 26.0.2 and Eclipse Adoptium Java 21.0.13; the actual packaged JAR also ran successfully on Java 21. Python 3 is optional for the packaged HTTP verification script; the Java application and JUnit suite do not require Python.

After the owner merges the reviewed Phase 2 backend into main:

```powershell
git clone https://github.com/Pragun3691/AeroRoster.git
cd AeroRoster
git switch main
git pull --ff-only origin main
git rev-parse HEAD
java -version
mvn -version
psql --version
```

Both teammates must record the same verified main SHA supplied by Member 1 before creating their feature branches. Until the merge is complete, main is not the Phase 2.6 baseline. Do not branch teammate work from an older main or from separate backend snapshots.

Create developer-local databases once using a PostgreSQL administrator. Skip objects that already exist; never reset a database to follow this guide:

```powershell
psql -h localhost -U postgres -d postgres -W
```

```sql
CREATE ROLE aeroroster_app LOGIN;
\password aeroroster_app
CREATE DATABASE aeroroster_dev OWNER aeroroster_app;
CREATE DATABASE aeroroster_test OWNER aeroroster_app;
\q
```

The password command prompts privately. The local owner account needs schema/table privileges for Flyway. Do not put a password in SQL files, committed application.properties, screenshots, command arguments or GitHub discussions. The test database must be named exactly aeroroster_test; its guard intentionally rejects development URLs and JDBC query parameters.

Set credentials only in the current PowerShell process:

```powershell
$localCredential = Get-Credential -UserName aeroroster_app -Message 'Local PostgreSQL credentials'
$env:DB_URL = 'jdbc:postgresql://localhost:5432/aeroroster_dev'
$env:DB_USER = 'aeroroster_app'
$env:DB_PASSWORD = $localCredential.GetNetworkCredential().Password
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/aeroroster_test'
$env:TEST_DB_USER = 'aeroroster_app'
$env:TEST_DB_PASSWORD = $env:DB_PASSWORD
mvn clean test
mvn package
mvn spring-boot:run
```

Flyway applies V1 (schema), V2 (fictional seed data) and V3 (edit versions) to a new database and validates checksums on subsequent starts. Hibernate then validates all entity mappings. An existing V2 database receives only the additive V3 migration; rows start at version zero and are retained. Stop all older application builds before migrating/running Phase 2.6; old writers do not maintain the new edit revisions. Do not use flyway clean, repair checksums or edit an applied migration to resolve a mismatch.

Open http://127.0.0.1:8080/ for the existing foundation page, http://127.0.0.1:8080/api-docs/index.html for the local API viewer and http://127.0.0.1:8080/api/v1/openapi.json for the specification. To run the packaged application, stop the Maven-run process and use `java -jar target/aeroroster-0.1.0-SNAPSHOT.jar`. Keep the default SERVER_ADDRESS=127.0.0.1. The homepage remains a foundation placeholder; frontend screens are Member 2's work.

Tests use separate TEST_DB_* settings and do not inherit DB_* credentials. Run one test suite at a time against the isolated test database. Rollback-based tests and owned-fixture cleanup retain seed records, although identity sequences may advance. No tests need aeroroster_dev writes. The fresh-migration test creates and removes only its own uniquely named schema inside aeroroster_test, requiring schema-creation privileges; it does not provision a separate PostgreSQL server.

| Symptom | Action |
| --- | --- |
| java/mvn/psql not found | Set PATH/JAVA_HOME to installed tools, reopen the shell, check versions. Do not change the target just to compile. |
| Connection refused/authentication failure | Start PostgreSQL, verify host/port, database existence and the appropriate DB_* or TEST_DB_* credentials. Never paste credentials into logs/issues. |
| Test database guard rejects URL | Use exactly jdbc:postgresql://localhost:5432/aeroroster_test with no query suffix; keep development data separate. |
| Flyway checksum mismatch | Check branch and V1/V2/V3 files against the reviewed baseline; ask Member 1 to investigate. Do not reset data or repair/edit checksums casually. |
| Hibernate validation fails | Confirm the correct database and successful V3 history before investigating mapping drift. |
| Port 8080 occupied | Stop your own older application or choose a free PORT; verify the responding process belongs to the new build. |
| API returns 403 | Use a loopback hostname and the same scheme/host/port. A different frontend dev-server port is cross-origin. Keep local integration changes coordinated with Member 1. |
| API returns 428/409 STALE_VERSION | Preserve the original edit token; follow the conflict workflow below. |

## Frozen API baseline: 1.1.0

The authoritative contract is [OpenAPI](../src/main/resources/static/api/v1/openapi.json), with the complete [42-operation table](09_PHASE24_REST_API.md#endpoint-inventory). The checksum in API_V1_BASELINE.sha256 uses UTF-8 with CRLF normalized to LF so Windows/Linux clones agree. OpenApiContractTest compares that digest, mapped paths/methods, record fields, integer widths, enums, schema references and conditional headers. To change a shared contract, obtain Member 1 review and update OpenAPI, the digest, tests and this handoff together. A passing checksum alone is not semantic compatibility review.

No operation paths were added or removed. Request bodies retain their existing editable fields. CrewView, AircraftView, FlightView, CrewQualificationView, UnavailabilityView and AssignmentView add server-managed nonnegative int64 version. Creation and single-resource reads return it, and list/page items include it. Reference resources, eligibility and coverage need no client version. Eligibility reasons remain the enum catalogue documented in Phase 2.3/OpenAPI.

| Conditional operation | Token resource |
| --- | --- |
| PUT /api/v1/crew/{crewId} | CrewView |
| PATCH /api/v1/crew/{crewId}/activity | CrewView |
| PUT /api/v1/aircraft/{aircraftId} | AircraftView |
| PATCH /api/v1/aircraft/{aircraftId}/activity | AircraftView |
| PUT /api/v1/flights/{flightId} | FlightView |
| PATCH /api/v1/flights/{flightId}/status | FlightView |
| PUT /api/v1/flights/{flightId}/cancellation | FlightView |
| PUT /api/v1/crew/{crewId}/qualifications/{qualificationId}/expiry | CrewQualificationView |
| DELETE /api/v1/crew/{crewId}/qualifications/{qualificationId} | CrewQualificationView |
| PUT /api/v1/crew/{crewId}/unavailability/{periodId} | UnavailabilityView |
| DELETE /api/v1/crew/{crewId}/unavailability/{periodId} | UnavailabilityView |
| PUT /api/v1/assignments/{assignmentId}/cancellation | AssignmentView |
| POST /api/v1/assignments/{assignmentId}/reassignments | Cancelled source AssignmentView |

Send the original snapshot as `X-Expected-Version: 12:3` for resource id 12/version 3. Use the held-link CrewQualificationView.id for qualification mutations, even though the URL uses the requirement qualificationId. This prevents a deleted/recreated link at version zero from matching an old token. Tokens accept positive int64 id and nonnegative int64 version separated by a colon; no quotes, weak tags, wildcard, negatives, whitespace or token lists. Missing token: 428 PRECONDITION_REQUIRED. Malformed/overflow token: 400 INVALID_PARAMETER. Changed version or identity: 409 STALE_VERSION. Absent resources still return 404 with a valid token. Invalid request bodies may fail validation before the precondition is evaluated.

Compare after locks and refresh, before state/business mutations. A stale command never becomes a successful no-op just because its desired status already matches. A current-token no-op returns 200 and retains version. Actual dirty updates increment version, including cancellation. Reassignment creates a version-zero row and retains the cancelled source; it does not consume/increment the source token. Duplicate active assignments and eligibility are rechecked. Creation/eligibility/ordinary reads do not require this header. Versions are per-row, not aggregate roster/coverage revisions; related-record safety still comes from locked service checks. Versions are not authentication or idempotency keys.

Growing collections use PageResponse(items,page,size,totalElements,totalPages,first,last); fixed catalogs and held qualifications are arrays. Pages are zero-based, default size 25, maximum 200, and page times size must fit 2147483647. In-range pages beyond available data are empty with last=true. Ordering is fixed; unknown sort/query fields are not dynamic entity access. Flight departure filters are inclusive from/exclusive until. Filters and parameter names are enumerated in OpenAPI.

Instant request strings need Z or an explicit offset and return UTC Z. LocalDate expiry accepts YYYY-MM-DD or explicit null where documented. Durations use ISO-8601 strings, e.g. PT10H. Stored times retain microsecond precision. POST creation returns 201 with relative Location; reads/updates return 200; successful deletes return 204 with no body. ApiError provides code,message,timestamp,path,fieldErrors,eligibility. Invalid enum/coercion/unknown or duplicate fields fail binding; copying a complete GET response into a write body fails because IDs, version and other managed fields are not editable.

## Member 2: frontend ownership and implementation boundaries

Build the dashboard, crew/qualifications/unavailability, aircraft, flights, manual assignment/rosters and conflict/coverage views on feature/frontend-dashboard. Use the existing Spring same-origin templates/static assets unless the team explicitly reviews a different local development arrangement. Do not relax CORS or expose the backend publicly to make a dev server work.

| Screen/workflow | Existing backend contract |
| --- | --- |
| Dashboard | Resource page totals and per-flight coverage; no aggregate dashboard endpoint. Page through results rather than assuming the first page contains all data. |
| Crew | Search/filter, create, detail, full editable-field PUT, activity changes; qualifications and unavailability are explicit subresources. Crew/aircraft physical delete is not provided. |
| Aircraft | Read type catalog, register/search/detail/edit, activity changes and paged flights. Type changes can reject when flight history exists. |
| Flights | Select airport/aircraft IDs, create/search/detail/edit, status/cancel, staffing coverage and roster/history. Active duties prevent material rescheduling/cancellation. |
| Qualifications | Read aircraft-type role requirements, grant, explicitly set/clear expiry and revoke when history permits. Preserve the held resource's identity/version. |
| Manual assignment | Select crew/flight/role, preview eligibility, show all reasons and policyNotice, commit, refresh roster and coverage. Preview does not reserve a slot. |
| Assignment lifecycle | Cancel retained assignments, show active/history distinction and reassign through the cancelled source resource. A new assignment has a new ID. |

Keep the original GET snapshot while a form is open. Construct only documented editable fields. Example:

```javascript
async function saveCrew(original, form) {
  const response = await fetch(`/api/v1/crew/${original.id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-Expected-Version': `${original.id}:${original.version}`
    },
    body: JSON.stringify({name: form.name, role: form.role,
      licenseNo: form.licenseNo, experienceYears: form.experienceYears,
      contactNo: form.contactNo})
  });
  const resourceOrError = await response.json();
  if (!response.ok) throw resourceOrError;
  return resourceOrError; // Store the returned version as the next edit snapshot.
}
```

Render fieldErrors beside their fields. Display every practical eligibility reason and coverage deficit, plus the returned academic-policy notice. On 409 STALE_VERSION preserve unsaved input, reload current data for comparison and ask the user to review/reapply before submitting with the newly reviewed token. Never silently fetch a fresh version and resubmit the old form. A 428 indicates a missing client integration header. Other 409 codes describe duplicate/current business restrictions; show their messages and eligibility where present. Handle 204 separately without JSON parsing. Do not issue database writes or embed PostgreSQL credentials in browser code.

## Member 3: scheduling enhancement ownership and boundaries

Work on feature/auto-allocation-reports. Reuse CrewService searches, CrewEligibilityService.check / DutyAssignmentService.check, DutyAssignmentService.assign/cancel/reassign/coverage, FlightService queries, and persisted assignment snapshots. Existing services/DTOs live in com.aeroroster.service and service.dto. For user-driven edits call overloads with `new ExpectedVersion(original.id(), original.version())`. Legacy overloads deliberately mean a trusted current-state command; do not use them to bypass concurrency on submitted forms. Do not expose new mutation routes that omit the client precondition.

Candidate inputs: page active crew by role; retrieve the flight and coverage; check each candidate's eligibility. The response contains dutyWindow, dutyLoad, policy, reasons and policyNotice. Eligibility is advisory; every commit rechecks locked state. Staffing requires one pilot, one copilot and AircraftType.minCabinCrew; extra cabin crew are allowed and extra pilot/copilot positions reject. Completed-flight staffing is not a completion gate under the current policy. All limits are illustrative academic settings, never certified aviation regulations.

Automatic allocation is not implemented here. Ranking, batch eligibility and allocation proposal contracts need review with Member 1. Do not select candidates in JavaScript and assume a later commit remains valid. Do not implement a naive multi-assignment loop in one outer transaction: acquiring another crew lock after flight/aircraft locks violates ordering. A future atomic batch orchestrator must acquire all affected crew IDs, then flight IDs, then aircraft IDs in ascending order before any child operation. The existing HTTP contract commits one assignment per call and does not promise all-or-nothing staffing of a whole flight.

Calendar inputs are paged flights, aircraft flights and per-crew assignment history/active snapshots. Current filters select departure instants; they do not supply an efficient global interval-overlap query including flights already airborne. Reports can use stored duty windows and statuses, including active historical duties on completed flights; cancelled duties do not count toward current load. Eligibility dutyLoad includes the proposed duty and is not a general historical reporting total. Audit rows do not contain full schedule revisions or assignment cancellation timestamps, and qualifications have no historical version table.

Performance: candidate-by-candidate eligibility is currently several queries per candidate. Page candidates, bound date ranges, measure queries and coordinate bulk projection APIs rather than issuing unbounded scans. DutyLoadCalculator uses exact interval clipping and finite extrema with quadratic work in relevant duty count; reuse it and SchedulingPolicy instead of inventing floating-point or SQL-only business rules. Planned gaps are bulk candidate/eligibility queries, reviewed allocation orchestration, cross-crew calendar overlap inputs and date-range duty aggregation/export. Do not implement these APIs or UIs in this milestone.

## Shared integration and GitHub workflow

Member 1 owns backend maintenance, migrations, API review and main integration. Member 2 owns frontend workflows. Member 3 owns reviewed scheduling enhancements and future reporting/calendar inputs. Discuss shared DTO/schema/controller edits before branching into conflicting implementations. Business rules stay in services; browser code never writes SQL; applied migrations never change; secrets never enter commits; shared API changes require coordinated review. No direct commits to main.

Owner actions before teammates start: review the Phase 2.6 diff and evidence, approve a checkpoint commit on feature/phase2-core, push that branch, open a PR targeting main, require review and green tests, and merge only after approval. Configure collaborator access and main branch protection on GitHub as available for the repository. Run local PostgreSQL checks if a CI database has not been configured; CI configuration is not created by this milestone. Record and share the resulting main SHA and OpenAPI baseline. No remote branches, push, PR or merge were executed by this task.

After both students clone the same verified main, Member 2 runs:

```powershell
git switch main
git pull --ff-only origin main
git switch -c feature/frontend-dashboard
```

Member 3 uses the same commands with `git switch -c feature/auto-allocation-reports`. Each student keeps their own local PostgreSQL databases and secrets. For a focused change:

```powershell
git status
git diff
mvn test
git diff --check
git add <reviewed-file-paths>
git diff --cached
git commit -m "feat: describe the focused change"
git push -u origin feature/frontend-dashboard
```

Replace the branch in the push command for Member 3. On GitHub choose Compare & pull request, base main, compare the feature branch. Describe the resulting behavior, changed contracts and validation; request Member 1 review. Never include credentials, database dumps, target output or unrelated files. Keep PRs small enough for a teammate to review and test. Do not merge your own unreviewed contract change.

To bring main into an existing feature branch without rewriting published history:

```powershell
git status
# First commit or deliberately stash your own unfinished work; start clean.
git fetch origin
git merge origin/main
```

If conflicts occur, inspect `git status` and the conflicted files. Coordinate shared API/schema decisions with Member 1, resolve markers preserving both intended behaviors, and never blindly take one side of a migration. Run relevant tests, inspect the diff, `git add <resolved-paths>`, then `git commit` to finish the merge and `git push`. Use `git merge --abort` to abandon an unresolved merge without discarding the pre-merge branch. Do not force-push shared work. After main changes, run migrations/tests on local databases; do not share or reset a teammate's database to fix drift.

## Project handoff checklist

- [x] Phase 2.5 checkpoint committed at 48237bf before implementation.
- [x] Java services and locking architecture retained; version columns are additive V3 only.
- [x] Original edit identity/version is exposed and required on all 13 existing-record mutation routes.
- [x] OpenAPI 1.1.0, conditional contract and LF-normalized baseline digest are synchronized.
- [x] Team responsibilities, branch names, local setup, secret handling and future API gaps are documented.
- [x] Final clean suite, package, packaged HTTP and preserved-development-data evidence recorded below.
- [ ] Owner reviews and commits all Phase 2.6 functionality; working tree is intentionally uncommitted until approval.
- [ ] Owner pushes/reviews/merges Phase 2 into main and shares the verified SHA with both teammates.
- [ ] Both teammates verify Java/Maven/PostgreSQL setup and run the test suite from that same main baseline.
- [x] Full suite and packaged JAR executed successfully under installed Java 21.0.13.

The unchecked owner/teammate items are real handoff gates; documentation does not imply that a commit, remote merge or fresh teammate-machine setup has already occurred.

## Verification evidence

Verified on 18 November 2026. No software was installed, no unrelated project was changed and no commit, push, remote branch, PR or merge was created. The prior Phase 2.5 portability gap is closed: Java 21 was discovered during this fresh inspection and used explicitly.

| Verification | Exact outcome |
| --- | --- |
| Initial targeted services/API run | 91 cases, one fixture assertion failure: the new test expected a lowercase registration even though identifiers normalize to uppercase. Corrected the owned fixture to use the established uppercase contract. |
| Targeted version/aircraft/workflow run | 19 cases, one real integration regression: @Version caused the initial pessimistic lookup of a stale cached aircraft to reject before refresh. Kept the original test assertion and corrected the lock helper to refresh under lock. |
| Final targeted lock/edit/workflow/freeze run | 32 tests, zero failures/errors/skips, BUILD SUCCESS. |
| mvn clean test, Java 26.0.2 | 188 tests, zero failures/errors/skips, BUILD SUCCESS, 01:47 min. |
| mvn package, Java 21.0.13 | 188 tests, zero failures/errors/skips, BUILD SUCCESS, 01:11 min; executable JAR produced. |
| Java 21 packaged HTTP script | 41 HTTP checks passed; persisted audit verified; owned fixtures cleaned and cleanup verified. |
| Actual packaged listener | Sole listener 127.0.0.1:18081, process 28780; stopped after verification. |
| Fresh migration and Hibernate | Empty owned test schema applies/validates V1/V2/V3, all ten entities validate, ten FKs and seed counts hold, six version columns initialize existing seeds to zero, audited ORM update rolls back. |
| Development preservation | All eleven development-table counts and ordered row-content signatures unchanged, including Flyway history. No development migration or application writes were performed. |
| Migration source safety | V1/V2 diff empty; test database history shows 1/true, 2/true, 3/true. V3 is the only new migration. |
| Contract and source checks | Frozen checksum matches, application bytecode is major 65 (Java 21), Python syntax/new-file whitespace pass, git diff --check exits 0. |
| Credential-pattern scan | 162 tracked/new source/documentation and current verification-log files, zero candidates; possible matched values were never printed. This is a heuristic scan, not a guarantee against every secret format. |

Final 188 cases in 19 classes include all 179 previous regression cases, eight new real-HTTP/JPA optimistic-concurrency cases and one new frozen-contract/header check. Existing HTTP workflow fixtures now fetch current tokens for their business-rule tests; the new stale-edit tests deliberately retain the original token. No tests were disabled, skipped or loosened to hide failures. All 13 conditional operations are exercised with stale tokens; their first successful edits remain intact. The simultaneous edit race observes both PostgreSQL row-lock waiters before release and verifies exactly one 200, one 409 STALE_VERSION, one version increment and the winning persisted name. Direct stale JPA flush independently throws OptimisticLockException. Existing crew/aircraft operations, qualification/unavailability safety, schedule/assignment races and sanitized REST error coverage remain green on both runtimes.

Reproduction commands and sanitized results (logs are ignored local artifacts):

```powershell
mvn '-Dtest=OptimisticConcurrencyHttpTest,CoreServicesIntegrationTest,AssignmentConcurrencyIntegrationTest,FreshMigrationVerificationTest,ApiIntegrationTest,OpenApiContractTest' test
# Initial fixture failure above; recorded in phase26-targeted.log.

mvn '-Dtest=OptimisticConcurrencyHttpTest,FlightConcurrencyIntegrationTest,OpenApiContractTest,BackendWorkflowHttpTest' test
# Exposed stale cached aircraft/version interaction; phase26-targeted-final.log.

mvn '-Dtest=OptimisticConcurrencyHttpTest,FlightConcurrencyIntegrationTest,AssignmentConcurrencyIntegrationTest,OpenApiContractTest,BackendWorkflowHttpTest' test
# Tests run: 32, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS

mvn clean test
# Tests run: 188, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS

$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn -version
# Apache Maven 3.10.1; Java version: 21.0.13, vendor: Eclipse Adoptium
mvn package
# Tests run: 188, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS

# In a dedicated verification process/shell, use test credentials only:
$env:DB_URL = 'jdbc:postgresql://localhost:5432/aeroroster_test'
$env:DB_USER = if ($env:TEST_DB_USER) { $env:TEST_DB_USER } else { 'postgres' }
$env:DB_PASSWORD = $env:TEST_DB_PASSWORD
$env:PORT = '18081'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:DEBUG = 'false'
& "$env:JAVA_HOME\bin\java.exe" -jar target/aeroroster-0.1.0-SNAPSHOT.jar
# Actual run used Start-Process -WindowStyle Hidden with redirected local logs.
# Starting AeroRosterApplication ... using Java 21.0.13
# Database: .../aeroroster_test (PostgreSQL 17.11)
# Successfully validated 3 migrations
# Initialized JPA EntityManagerFactory for persistence unit 'default'
# Tomcat started on port 18081 (http)
# Started AeroRosterApplication in 20.25 seconds

# Run in a second verification shell with the same TEST_DB_* credentials:
python scripts/verify_phase25_package.py
# Packaged JAR: 41 HTTP checks passed; persisted audit verified; owned fixtures cleaned

git diff --check
# Exit 0; no whitespace errors (Windows LF/CRLF conversion notices may appear).
```

The script retains its original filename for continuity but verifies the current conditional contract. It checks homepage/CSS/docs/spec, catalogs, validation/pagination extremes, host/origin restrictions, duplicate JSON keys, creation, missing edit precondition, a successful first edit, stale second edit and persisted winner, assignment preview/commit/rejection, coverage, cancellation/reassignment, history and flight audit. Only recorded owned IDs are removed. The actual background process was checked with Get-NetTCPConnection before Stop-Process; no verification JAR remains running.

Development snapshots store counts plus ordered content hashes, not row exports. The final query repeated the initial signature computation for each table, ordered by id (Flyway history by installed_rank). V3 remains pending on aeroroster_dev until the owner starts the upgraded build; its additive behavior was verified in the isolated test database and empty-schema rehearsal. A new separate PostgreSQL server/database provisioning exercise and actual teammate-machine setup were not performed during this local check.

## Merge recommendation and owner actions

**Phase 2 is technically ready for owner review and merge after an approved checkpoint commit and PR.** Stale administrative overwrites are rejected across all existing mutable API resources, scheduling locks retain their tested behavior and API 1.1.0 is frozen. The build, full regression suite, migration rehearsal and packaged workflows pass on Java 21. Member 2 can implement the documented local frontend against these contracts; Member 3 can build on existing eligibility/coverage/single-assignment services within the documented future API boundaries.

This working tree is intentionally uncommitted on feature/phase2-core at 48237bf. The owner must review the diff, commit all reviewed Phase 2.6 files, push/open a PR, review/merge into main and share that resulting main SHA. Both teammates should then clone/branch from the same main SHA and verify their own environment. The checklist's commit/merge/teammate setup gates remain unchecked until those actions occur. Proposed commit:

```text
feat: add versioned edits and freeze Phase 2 backend contracts
```

Remaining limits: unauthenticated local-only deployment; no production security approval; row-scoped versions with trusted internal command overloads; no whole-roster version/idempotency/batch allocation contract; existing limited audit/qualification history; future bulk eligibility, calendar overlap and reporting inputs; and owner-managed CI/branch protection. These do not reopen the tested stale-form overwrite defect. Do not run older writers against the V3 database. Stop after Phase 2.6; no frontend, allocation, calendar/report UI, authentication or deployment work was performed.

## Changed file inventory

49 files: 41 modified and eight new. The scope is version persistence/DTOs, conditional service/controller commands, contract freeze, regression fixtures/tests, the packaged check and team documentation.

- `AGENTS.md`
- `README.md`
- `docs/03_RULES_AND_DECISIONS.md`
- `docs/04_PHASE_ROADMAP.md`
- `docs/09_PHASE24_REST_API.md`
- `docs/10_PHASE25_BACKEND_VERIFICATION.md`
- `docs/11_PHASE26_TEAM_HANDOFF.md`
- `docs/API_V1_BASELINE.sha256`
- `scripts/verify_phase25_package.py`
- `src/main/java/com/aeroroster/persistence/entity/Aircraft.java`
- `src/main/java/com/aeroroster/persistence/entity/Crew.java`
- `src/main/java/com/aeroroster/persistence/entity/CrewQualification.java`
- `src/main/java/com/aeroroster/persistence/entity/CrewUnavailability.java`
- `src/main/java/com/aeroroster/persistence/entity/DutyAssignment.java`
- `src/main/java/com/aeroroster/persistence/entity/Flight.java`
- `src/main/java/com/aeroroster/service/AircraftService.java`
- `src/main/java/com/aeroroster/service/CrewService.java`
- `src/main/java/com/aeroroster/service/CrewUnavailabilityService.java`
- `src/main/java/com/aeroroster/service/DutyAssignmentService.java`
- `src/main/java/com/aeroroster/service/ExpectedVersion.java`
- `src/main/java/com/aeroroster/service/FlightService.java`
- `src/main/java/com/aeroroster/service/QualificationService.java`
- `src/main/java/com/aeroroster/service/ServiceLocks.java`
- `src/main/java/com/aeroroster/service/ServiceTransactions.java`
- `src/main/java/com/aeroroster/service/ServiceViews.java`
- `src/main/java/com/aeroroster/service/dto/AircraftView.java`
- `src/main/java/com/aeroroster/service/dto/AssignmentView.java`
- `src/main/java/com/aeroroster/service/dto/CrewQualificationView.java`
- `src/main/java/com/aeroroster/service/dto/CrewView.java`
- `src/main/java/com/aeroroster/service/dto/FlightView.java`
- `src/main/java/com/aeroroster/service/dto/UnavailabilityView.java`
- `src/main/java/com/aeroroster/service/exception/StaleVersionException.java`
- `src/main/java/com/aeroroster/web/api/AircraftController.java`
- `src/main/java/com/aeroroster/web/api/ApiExceptionHandler.java`
- `src/main/java/com/aeroroster/web/api/ApiPreconditions.java`
- `src/main/java/com/aeroroster/web/api/AssignmentController.java`
- `src/main/java/com/aeroroster/web/api/CrewController.java`
- `src/main/java/com/aeroroster/web/api/FlightController.java`
- `src/main/java/com/aeroroster/web/api/QualificationController.java`
- `src/main/java/com/aeroroster/web/api/UnavailabilityController.java`
- `src/main/resources/db/migration/V3__resource_versions.sql`
- `src/main/resources/static/api/v1/openapi.json`
- `src/test/java/com/aeroroster/persistence/PersistenceIntegrationTest.java`
- `src/test/java/com/aeroroster/support/EditTokens.java`
- `src/test/java/com/aeroroster/verification/BackendWorkflowHttpTest.java`
- `src/test/java/com/aeroroster/verification/FreshMigrationVerificationTest.java`
- `src/test/java/com/aeroroster/web/api/ApiIntegrationTest.java`
- `src/test/java/com/aeroroster/web/api/OpenApiContractTest.java`
- `src/test/java/com/aeroroster/web/api/OptimisticConcurrencyHttpTest.java`
