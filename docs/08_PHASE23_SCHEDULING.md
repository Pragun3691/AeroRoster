# Phase 2.3 crew assignment and scheduling

## Inspection and implementation plan

Phase 2.2 is committed at `0c8f897` on `feature/phase2-core`. The working tree was clean before this milestone. The owner explicitly authorized Phase 2.3. Inspection covered the existing ten-table V1/V2 schema, entities, repositories, DutyWindow, Phase 2.2 contracts, transaction wrapper, refreshed pessimistic locks and existing tests.

Implement a pure Java duty-load calculator and rule evaluator, validated scheduling configuration, immutable eligibility/coverage records, assignment operations and crew-unavailability administration. Reuse existing assignment DTOs and transaction/locking helpers. Add targeted repository projections so safety decisions read current database values after lock waits. Preserve the applied schema and use the existing partial unique index for active crew/flight pairs.

Retain the established one-hour preflight and 30-minute postflight buffers, ten-hour rest, 14-hour UTC-calendar-day limit and inclusive qualification expiry on the flight's UTC departure date. Rolling windows have no previously approved numeric limits, so use the requested academic defaults of ten hours per rolling 24 hours and 40 hours per rolling seven days in addition to the daily limit. Centralize all durations and validate configuration. Derived duty windows are stored as assignment snapshots.

Writes lock crew, flight and aircraft in that order and recheck all eligibility rules before insertion. The shared crew lock serializes assignment, crew/qualification changes and unavailability edits. The flight lock serializes unique-role capacity, assignment cancellation and flight changes. Keep cancelled rows as history. Use the existing refreshed lock helper and fresh scalar projections for dependent decision data.

The main risks are partial rolling-window calculations, cross-midnight duties, stale cached entities, role-slot races and inconsistent lock ordering. Verify pure duration calculations at boundaries, PostgreSQL service outcomes and persisted state, actual competing transactions on separate connections, rollback, existing tests, Flyway/Hibernate startup and a manual service workflow. Run `mvn clean test`, targeted concurrency tests, `mvn package` and `git diff --check`.

## Architecture and contracts

All services use constructor injection and the existing ServiceTransactions boundary. Public contracts are immutable records. Persistence entities remain internal to the transaction.

| Class | Responsibility |
| --- | --- |
| SchedulingPolicy | Validated configuration and duty-window derivation |
| DutyLoadCalculator | Exact rolling and UTC-day interval accounting |
| DutyRules | Pure overlap, rest, unavailability and load decisions |
| CrewEligibilityService | Database-backed qualification/state/capacity checks plus pure duty rules |
| DutyAssignmentService | Locked assignment writes, cancellation, history and staffing coverage |
| CrewUnavailabilityService | Locked unavailability administration and conflict checks |
| EligibilityDecision | Eligibility, derived DutyWindow, calculated DutyLoad, all applicable reasons, policy values and academic-policy notice |
| SchedulingReason | Stable enum code and readable explanation |
| FlightCrewCoverage / RoleCoverage | Required, active and missing counts, staffing result, reasons, status and policy notice |
| AssignmentIdentity / QualificationValidity | Scalar query projections for current decision data |

| Service | Public methods |
| --- | --- |
| CrewEligibilityService | `check(AssignmentRequest)` |
| DutyAssignmentService | `check`, `assign`, `get`, `forFlight`, `activeForCrew`, `historyForCrew`, `historyForFlight`, `cancel`, `reassign`, `coverage` |
| CrewUnavailabilityService | `add`, `list`, `update`, `remove`, `conflicts` |

`AssignmentRequest` contains crew ID, flight ID and assigned CrewRole. The service derives duty endpoints from the flight and configured buffers. Clients cannot submit a shorter duty window. `SchedulingPolicy.dutyWindow` rejects invalid flight times, precision loss and a window that fails to enclose the flight. `AssignmentView` remains the existing Phase 2.2 record, including the stored duty snapshot, status and database-generated creation timestamp.

`UnavailabilityRequest` contains start/end instants and a required reason of at most 200 characters. Add/list/conflict operations take a crew ID. Updates and removal take both crew ID and period ID, enforcing ownership. All IDs must be positive. Timestamp validation retains the existing microsecond precision contract.

## Eligibility rules and stable reasons

The preview accumulates independent problems whenever their prerequisites are available. Missing crew and missing flight are both reported when both IDs are absent. A missing flight prevents checks that depend on its schedule or aircraft. Malformed requests still raise InvalidInputException before database use.

| Rule | Reason code |
| --- | --- |
| Crew/flight ID absent | CREW_NOT_FOUND, FLIGHT_NOT_FOUND |
| Inactive crew | CREW_INACTIVE |
| Flight status other than SCHEDULED | FLIGHT_NOT_SCHEDULED |
| Inactive aircraft | AIRCRAFT_INACTIVE |
| Requested role differs from crew role | ROLE_MISMATCH |
| Aircraft-type/role qualification absent | MISSING_QUALIFICATION |
| Qualification expired before UTC departure date | QUALIFICATION_EXPIRED |
| Duty window cannot be derived safely | INVALID_DUTY_WINDOW |
| Same crew already active on flight | DUPLICATE_ACTIVE_ASSIGNMENT |
| Pilot/copilot role already occupied | FLIGHT_ROLE_FILLED |
| Unavailability intersects duty | CREW_UNAVAILABLE |
| Another ACTIVE duty overlaps | CREW_TIME_CONFLICT |
| Gap to another separate ACTIVE duty below minimum | INSUFFICIENT_REST |
| Rolling limits exceeded | ROLLING_24_HOUR_LIMIT, ROLLING_7_DAY_LIMIT |
| Established UTC-day limit exceeded | UTC_DAILY_DUTY_LIMIT |

Null qualification expiry is open-ended. Expiry is inclusive on the flight's UTC departure date, retaining the previously approved rule even if duty begins on the previous date or ends after midnight. Aircraft type and requested role must both match the qualification. A crew member may retain an expired qualification as a valid administrative record.

The preview is advisory and holds no scheduling locks. `assign` and `reassign` evaluate the same rules again after locking the current crew, flight and aircraft. A rejected write raises AssignmentRejectedException, a BusinessConflictException carrying the full EligibilityDecision through `getDecision()`. Missing referenced records during locking raise the existing ResourceNotFoundException. Existing integrity and transaction errors retain the Phase 2.2 business exception translation.

## Academic configuration

These are illustrative course settings and provide no certification of compliance with DGCA, FAA or other aviation regulations. Frontend and future report code must display `policyNotice` from eligibility or coverage results with scheduling decisions.

| Property under `aeroroster.scheduling` | Default | Meaning |
| --- | --- | --- |
| preflight-buffer | PT1H | Duty begins before departure |
| postflight-buffer | PT30M | Duty ends after arrival |
| minimum-rest | PT10H | Minimum gap between separate duties |
| maximum-rolling24-hours | PT10H | Maximum duty within any affected rolling 24-hour interval |
| maximum-rolling7-days | PT40H | Maximum duty within any affected rolling seven-day interval |
| maximum-utc-day | PT14H | Existing maximum charge within an affected UTC calendar date |

Spring binds ISO-8601 Duration values through ConfigurationProperties. Defaults are centralized in SchedulingPolicy. Buffers and rest must be nonnegative, duty limits must be positive, and values must have at most microsecond precision. Invalid configuration fails startup. Zero rest can be explicitly configured for experiments. Stored assignment windows continue to use the buffers in effect when they were created. A reassignment creates a fresh snapshot using current configuration and flight details.

For example, a local properties override can raise the illustrative rolling cap while retaining the separate UTC-day cap.

```properties
aeroroster.scheduling.maximum-rolling24-hours=PT12H
aeroroster.scheduling.maximum-rolling7-days=PT40H
aeroroster.scheduling.minimum-rest=PT10H
```

Policy changes apply to subsequent decisions and preserve existing assignment snapshots. Revalidating a full roster after policy changes is a future administrative workflow.

## Duty hours, rest and boundaries

All times are Instant values. DutyWindow is the shared immutable half-open `[start, end)` interval. Stored snapshots satisfy `dutyStart <= departure < arrival <= dutyEnd`. Existing ACTIVE assignment windows are read directly from PostgreSQL. Cancelled rows contribute no scheduling load.

The load of a window is the sum of each duty's clipped intersection with it. Calculations use Duration arithmetic with nanosecond accuracy and no floating-point conversion. Each duty contributes only the duration of its intersection with the window. Duties across midnight and multiple dates are handled by the same clipping operation.

For a rolling period W, the summed load as a function of window start is piecewise linear. Its slope changes at each duty start, duty end, start minus W and end minus W. Evaluate those finite boundaries to obtain the exact maximum. Windows touching the proposed duty are included by continuity at their boundaries. This checks the periods affected by a new assignment without letting unrelated distant legacy violations prevent it.

Relevant history is queried from proposed start minus `max(seven days, minimum rest)` to proposed end plus that margin, including any long duty crossing the query bounds. The pure calculator considers the proposed snapshot together with that history. It evaluates both past and future scheduled duties.

UTC-day accounting clips at UTC midnight. A finite set of interval first/last dates and the day after each first date covers partial boundary dates and the constant-load interior runs, avoiding a loop across every date of a long duty. An independent exhaustive grid oracle checks the rolling and daily extrema over 40 deterministic generated cases.

An overlap is `first.start < second.end && first.end > second.start`. Touching endpoints have zero overlap. Rest is checked separately in both temporal directions for non-overlapping duties. A touching pair has zero rest and therefore fails the default ten-hour minimum. A gap equal to the minimum passes. Limits are inclusive, so only a strictly greater duty load is rejected. Overlapping legacy duties are charged separately and also produce a time-conflict reason.

## Staffing and history

Each flight requires exactly one PILOT, exactly one COPILOT and at least AircraftType.minCabinCrew CABIN_CREW assignments. The shared flight row lock serializes checks of pilot/copilot role occupancy. Cabin crew can exceed the minimum. The same crew/flight pair cannot hold two ACTIVE rows, regardless of requested role, and crew role matching supplies another check against filling multiple roles.

Incremental assignment is allowed. Coverage reports missing roles through MISSING_PILOT, MISSING_COPILOT and MISSING_CABIN_CREW, along with required/active/missing counts. Legacy overfilled pilot/copilot data produces EXCESS_PILOTS or EXCESS_COPILOTS and an unsatisfied staffing result. Coverage describes active role counts and includes flight status. Flight completion retains the Phase 2.2 status-transition rules. Coverage is a separate staffing contract for the frontend.

Cancellation updates status to CANCELLED and retains the assignment ID, role, duty snapshot and creation timestamp. Repeating cancellation is idempotent. `reassign(cancelledAssignmentId)` reuses the previous crew, flight and role, checks current eligibility and inserts a new ACTIVE row with a new ID. Calling it on an active row is a conflict. Calling it again while a replacement is active is rejected as a duplicate or occupied role.

`forFlight` and `activeForCrew` return ACTIVE records. History methods include cancelled rows. History is ordered by duty start and ID. Existing CrewService.assignments behavior continues to return history. The V1 partial unique index remains the final crew/flight uniqueness boundary.

## Unavailability

Create and update lock the crew first, validate increasing bounds, reject overlaps with another unavailable period and reject intersections with any ACTIVE duty. Editing a period excludes itself from the overlap query. Adjacent unavailable periods are allowed. Updates lock and refresh the owned period before applying changes. Removal locks the same crew and period. It reduces unavailable time and cannot invalidate an existing duty.

The same crew lock protects assignment and unavailability administration. Whichever competing operation commits first is visible to the second operation's recheck. Read-only `conflicts` reports existing unavailable time for a proposed duty interval and remains an advisory query.

## Transaction and concurrency strategy

Writes use the existing Spring transactional proxy, now explicitly requesting READ_COMMITTED isolation. The shared write boundary checks the actual transaction-bound JDBC connection isolation and rejects joining an incompatible outer transaction. It also rejects a read-only outer transaction. At repeatable-read isolation, locking an unchanged parent row alone does not make a newly inserted child visible to an older snapshot, so an explicit write isolation contract is necessary for this locking protocol.

| Operation | Lock order |
| --- | --- |
| Assign | Crew, flight, aircraft, then recheck and insert |
| Cancel/reassign | Read scalar immutable ownership, then crew, flight, aircraft, assignment |
| Unavailability create/edit/remove | Crew, then owned unavailable period when it exists |
| Qualification expiry/revocation | Crew, then held qualification |
| Existing crew administration | Crew |
| Existing flight edits/status | Flight, then affected aircraft in ascending ID order |
| Existing aircraft administration | Aircraft |

Multiple known rows at one level must be acquired in ascending ID order. A future batch orchestrator must acquire all required parent rows in the global crew → flight → aircraft order before processing the batch. Individual operations use REQUIRED propagation. Callers own final commit when supplying an outer transaction and must let failures abort the complete operation. Arbitrary reordered compositions of operations can still encounter a database-detected deadlock and must retry the complete transaction when appropriate.

Lock acquisition and refresh reuse ServiceLocks. Dependent eligibility data uses scalar projections for qualification expiry, active duty windows, unavailability windows and role occupancy, preventing the first-level persistence cache from hiding committed changes. The assignment write, cancellation or revalidation holds its locks through flush and commit. An assignment never trusts an earlier preview.

The crew lock prevents simultaneous double booking, qualification changes, deactivation and unavailable-time contradictions. The flight lock prevents unique-role overfill and serializes assignment changes against flight edits and cancellation. The aircraft lock shares the Phase 2.2 administration/scheduling protocol and protects aircraft state.

The database still enforces IDs, foreign keys, valid enum strings, positive windows and the active crew/flight partial unique index. Unique-role capacity and crew time/rest/load rules are enforced by the cooperating service protocol. Direct repository or external SQL writes can bypass these business rules. No Flyway migration is required. FlightAudit remains trigger-managed.

## Phase 2.2 integration changes

- ServiceLocks now supports refreshed child assignment, unavailability and held-qualification records after the owning parent locks.
- QualificationService expiry changes and revocation refresh the held qualification and read history departure instants through scalar projections. This closes a cache case where a concurrent renewal, flight reschedule and new assignment could otherwise be hidden by old managed values.
- ServiceUnitOfWork explicitly enforces a writable READ_COMMITTED transaction, making the isolation assumption in the existing aircraft lock protocol enforceable too.
- Flight cancellation's message now tells the caller to cancel active duty assignments first. Cancellation still rejects a staffed flight. After cancelling its active rows, the existing FlightService.cancel operation can succeed. A competing assignment can cause it to reject safely.
- Existing deactivation, type/role changes, active-assignment flight-edit checks and qualification-history protections remain in use and are covered by regression tests.

## Frontend usage and examples

Use crew, flight and role values selected through the existing services. Display the eligibility reasons and policyNotice. Successful preview does not reserve a slot.

```java
AssignmentRequest request = new AssignmentRequest(crewId, flightId, CrewRole.PILOT);
EligibilityDecision preview = dutyAssignmentService.check(request);
if (preview.eligible()) {
    AssignmentView assignment = dutyAssignmentService.assign(request);
}
FlightCrewCoverage coverage = dutyAssignmentService.coverage(flightId);
```

For a qualified active pilot on a scheduled active A320 flight departing at 05:00 UTC and arriving at 07:00 UTC, the defaults derive duty `[04:00, 07:30)`. With no conflicts and available pilot capacity, assignment succeeds. The returned record contains the generated assignment ID and ACTIVE status.

A second pilot on that flight receives FLIGHT_ROLE_FILLED. Assigning the same pilot to an overlapping duty can report CREW_TIME_CONFLICT. A separate nearby duty can additionally produce INSUFFICIENT_REST. An inactive pilot requested as COPILOT without that qualification and with overlapping training can report CREW_INACTIVE, ROLE_MISMATCH, MISSING_QUALIFICATION and CREW_UNAVAILABLE in the same decision.

Catch AssignmentRejectedException around writes and display its current decision. Catch the existing typed exceptions for invalid requests or missing records. Cancellation and reassignment return AssignmentView. All record results are independent of lazy JPA relationships after the service transaction closes.

## Testing and verification

Use the existing guarded aeroroster_test database and local TEST_DB credentials described in the Phase 2.1 notes. Run one suite at a time against this dedicated database. Tests fail on unavailable PostgreSQL or an incorrect target. Ordinary service tests roll back. Committed concurrency fixtures use unique names, separate connections, worker shutdown and exact-ID cleanup in finally/AfterEach. They retain all seeded records.

```powershell
mvn clean test
mvn '-Dtest=AssignmentConcurrencyIntegrationTest,FlightConcurrencyIntegrationTest' test
mvn package
git diff --check
```

Concurrency tests hold a parent row lock, start two requests and observe both waiting in pg_stat_activity before releasing the lock. They verify the actual losing business reason and final persisted state for double booking, pilot role capacity, unavailable time, crew activation, qualification expiry/revocation and flight cancellation. Other real-transaction tests verify stale qualification/flight caches, full rollback after a flushed insert and rejection of incompatible outer transaction modes.

Verified on 18 November 2026 with JDK 26.0.2, Java release 21 compilation, Maven 3.10.1, Spring Boot 4.1.2 and PostgreSQL 17.11. Execution on a separate Java 21 runtime remains unverified.

| Check | Exact final result |
| --- | --- |
| `mvn clean test` | BUILD SUCCESS, exit 0, 118 tests, 0 failures, 0 errors, 0 skipped, completed 17:25:23 IST |
| Targeted concurrency command above | BUILD SUCCESS, exit 0, 14 tests, 0 failures, 0 errors, 0 skipped, completed 17:25:43 IST |
| `mvn package` | BUILD SUCCESS, exit 0, 118 tests, 0 failures, 0 errors, 0 skipped, completed 17:26:10 IST |
| Existing Phase 1/2.1/2.2 tests | All 59 passed |
| DutyRulesTest | 13 passed, including the 40-scenario independent oracle |
| SchedulingPolicyTest | 7 passed, including startup rejection for invalid configuration |
| SchedulingIntegrationTest | 27 passed |
| AssignmentConcurrencyIntegrationTest | 12 passed, including competing writes, cached-state cases, rollback and outer transaction guards |
| Existing FlightConcurrencyIntegrationTest | 2 passed, included in both the full suite and targeted run |
| Flyway / Hibernate | Both applied migrations validated, schema v2 up to date, entity manager initialized with schema validation in the packaged application and tests |
| Manual scheduling workflow | Exit 0, complete staffing, structured duplicate rejection, stored duty snapshot, unavailability protection, cancellation/reassignment and retained history verified |
| Manual workflow rollback | All ten test-table counts restored |
| Packaged-JAR HTTP smoke | Confirmed startup at 17:29:39 IST on port 18081, homepage HTTP 200 and CSS HTTP 200, verification process stopped afterward |
| Migration source check | `git diff --exit-code -- src/main/resources/db/migration` passed |
| Whitespace check | `git diff --check` passed |

The manual workflow constructed a flight with one qualified pilot, one qualified copilot and two qualified cabin crew through the services. Coverage reported required/active/missing counts of 1/1/0, 1/1/0 and 2/2/0. The pilot duty snapshot was `[2027-01-10T04:00:00Z, 2027-01-10T07:30:00Z)`. A repeated assignment reported FLIGHT_ROLE_FILLED, DUPLICATE_ACTIVE_ASSIGNMENT and CREW_TIME_CONFLICT. Cancelling/reassigning the pilot retained five history records and four active records. Cancelling all active assignments then allowed the flight to be cancelled, retaining five history records and zero active records. The enclosing test transaction rolled back.

Read-only development checks confirmed successful V1/V2 entries and the counts below. The dedicated test database retained the same seed counts after rollback and exact-ID fixture cleanup. Queries for the unique S23/F23 concurrency prefixes and SM23 manual prefixes returned zero remaining crew, aircraft and flight fixtures.

| Table | Count |
| --- | --- |
| airport | 5 |
| aircraft_type | 2 |
| aircraft | 3 |
| crew | 12 |
| qualification | 6 |
| crew_qualification | 12 |
| flight | 5 |
| crew_unavailability | 1 |
| duty_assignment | 4 |
| flight_audit | 5 |

Local ignored evidence is in `phase23-test.log`, `phase23-concurrency.log`, `target/phase23-package.log`, `target/phase23-manual.log`, `target/phase23-runtime.log`, `target/phase23-runtime-error.log` and `target/surefire-reports`. The manual runner is `target/Phase23Smoke.java`, using dependencies extracted from the packaged JAR under `target/phase23-smoke-lib`. The executable artifact is `target/aeroroster-0.1.0-SNAPSHOT.jar`.

The first targeted concurrency build found two test-harness compilation errors from calling a List method on a Stream. Both were corrected and the final targeted/full runs passed. The first HTTP verification script used a reserved PowerShell variable and was corrected before the successful final smoke check. Successful Maven runs emitted optional repository metadata HTTP 401 and JVM dynamic-agent/class-sharing warnings. Git emitted line-ending conversion notices. No unresolved implementation failures remain.

No manual setup is required on this machine. Fresh machines need PostgreSQL and the dedicated aeroroster_test database/credentials documented in Phase 2.1. The course-approval item remains owner-managed in the existing Phase 1 checklist.

## Created and modified files

Created production files under `src/main/java/com/aeroroster/`.

```text
domain/scheduling/AssignmentIdentity.java
domain/scheduling/DutyLoad.java
domain/scheduling/DutyLoadCalculator.java
domain/scheduling/DutyRules.java
domain/scheduling/QualificationValidity.java
domain/scheduling/SchedulingPolicy.java
domain/scheduling/SchedulingReason.java
domain/scheduling/SchedulingReasonCode.java
service/CrewEligibilityService.java
service/CrewUnavailabilityService.java
service/DutyAssignmentService.java
service/dto/AssignmentRequest.java
service/dto/EligibilityDecision.java
service/dto/FlightCrewCoverage.java
service/dto/RoleCoverage.java
service/dto/UnavailabilityRequest.java
service/dto/UnavailabilityView.java
service/exception/AssignmentRejectedException.java
```

Also created.

```text
docs/08_PHASE23_SCHEDULING.md
src/test/java/com/aeroroster/domain/scheduling/DutyRulesTest.java
src/test/java/com/aeroroster/domain/scheduling/SchedulingPolicyTest.java
src/test/java/com/aeroroster/service/AssignmentConcurrencyIntegrationTest.java
src/test/java/com/aeroroster/service/SchedulingIntegrationTest.java
```

Modified existing files.

```text
AGENTS.md
README.md
docs/03_RULES_AND_DECISIONS.md
docs/04_PHASE_ROADMAP.md
src/main/java/com/aeroroster/persistence/repository/CrewQualificationRepository.java
src/main/java/com/aeroroster/persistence/repository/CrewUnavailabilityRepository.java
src/main/java/com/aeroroster/persistence/repository/DutyAssignmentRepository.java
src/main/java/com/aeroroster/service/FlightService.java
src/main/java/com/aeroroster/service/QualificationService.java
src/main/java/com/aeroroster/service/ServiceConfiguration.java
src/main/java/com/aeroroster/service/ServiceLocks.java
src/main/java/com/aeroroster/service/ServiceUnitOfWork.java
```

## Limitations and Phase 4 handoff

This is an academic policy model. Qualification validity uses the approved UTC departure-date rule. Assignment history retains cancelled snapshots, but V1 has no cancellation timestamp, assignment action audit or exact qualification-reference snapshot. Existing flight/qualification audit limitations from Phase 2.2 continue to apply. The engine counts ACTIVE status as required by the project rules.

Coverage reports staffing and flight status independently of the Phase 2.2 completion transition. Eligibility permits historical data entry for SCHEDULED flights. Policy changes require a separate roster-wide review workflow if existing assignments must be revalidated. Direct database writers must adopt the same protocol or require separately approved database constraints.

Phase 4 candidate selection can reuse CrewEligibilityService.check to inspect candidates and explain rejection. Selection/ranking and automatic allocation are future work. Any eventual confirmation must call assign again and handle its current result. Batch allocation requires ordered batch locks and transaction design before implementation.

## Acceptance recommendation

Phase 2.3 is ready for team review and acceptance. Eligibility, assignment history, staffing, exact duty/rest rules, unavailability, transaction integration, PostgreSQL races, migration validation, manual workflow and handoff documentation have been verified. Continue only after the team's milestone review.
