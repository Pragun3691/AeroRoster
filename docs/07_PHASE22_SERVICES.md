# Phase 2.2 core Java services

## Scope and implementation plan

The project owner authorized Phase 2.2 on 18 November 2026. Implement service operations over the existing persistence layer, separate record DTOs, validation, typed business exceptions, transactional writes and concurrency protection. Keep reference data read-only. Verify application behavior against PostgreSQL, retain existing tests and record evidence for the team.

The main risks are competing aircraft schedules, duplicate natural identifiers, stale JPA state after a lock wait, and administrative changes that invalidate qualifications or duty history. Address them with row locks, refresh under lock, database uniqueness, explicit history restrictions and real two-transaction regression tests.

## Service contracts and operations

Inject the concrete Spring services through constructors. Each operation maps to immutable DTO records inside its transaction. DTOs contain values and IDs and can be used after the transaction closes. Services expose no JPA entities. Interfaces have been reserved for a future need for interchangeable implementations.

| Service | Public operations | Returned contracts |
| --- | --- | --- |
| CrewService | `create`, `get`, `search`, `update`, `setActive`, `assignments` | CrewView, Page of CrewView, List of AssignmentView |
| AirportService | `get`, `search` | AirportView, List of AirportView |
| AircraftTypeService | `get`, `list` | AircraftTypeView, List of AircraftTypeView |
| AircraftService | `register`, `get`, `search`, `update`, `setActive` | AircraftView, Page of AircraftView |
| QualificationService | `requirements`, `forCrew`, `grant`, `updateExpiry`, `revoke` | QualificationView and CrewQualificationView lists, CrewQualificationView, void |
| FlightService | `create`, `get`, `search`, `forAircraft`, `update`, `cancel`, `changeStatus` | FlightView, Page of FlightView, List of FlightView |

`setActive(id, true)` activates and `setActive(id, false)` deactivates. Qualification mutations address the crew ID and qualification-requirement ID. `QualificationExpiryRequest(null)` explicitly makes an expiry open-ended. Request records describe a full update of editable fields. Flight status changes use their own operation, preventing schedule updates from bypassing transition rules.

Search uses `CrewFilter`, `AircraftFilter` and `FlightFilter` with `PageRequest.of(page, size)`, using zero-based pages and sizes from 1 to 200. Services supply stable ordering, so pass an unsorted page request. Optional filter fields accept null. Crew search matches name or license, aircraft search matches registration or model, and flight search matches flight number. These are case-insensitive literal substring searches, with percent, underscore and the escape character treated as literal input. Airport search additionally matches city and country.

Flight search ranges filter departure instants using inclusive `departureFrom` and exclusive `departureUntil`. Either bound may be omitted. When both are supplied, the end must follow the start. Aircraft-specific flight retrieval includes all statuses, ordered by departure. Crew assignment retrieval includes active and cancelled history, ordered by duty start and ID.

## Validation and business policies

### Crew

- Required trimmed name, role, license, contact and nonnegative integer experience. Lengths follow V1. Contact accepts 7 to 15 digits, an optional leading plus, spaces, parentheses and hyphens within the existing 20-character column. This validates application input format and does not verify a real phone number.
- Licenses, aircraft registrations and flight numbers are stripped and normalized using locale-independent uppercase. Duplicate checks are case-insensitive and exclude the current record on update. Database unique constraints provide the final concurrency check for normalized writes.
- Crew start active. Role changes require the crew member to have neither held qualifications nor assignment history. Name, contact, license and experience corrections remain available.
- Deactivation rejects any ACTIVE assignment whose duty end is after the application clock, covering ongoing and future duties. Past duties and cancelled assignment history permit deactivation. The operation retains assignments and returns an explicit business error when blocked.
- Phase 2.3 must check the locked crew member's active flag before creating any assignment. This milestone provides crew administration and assignment reads.

### Aircraft and reference data

- Registration requires an existing aircraft type and a unique normalized registration. New aircraft start active. Existing type and airport records are read-only through these services.
- Changing an aircraft's type is permitted while it has no referenced flights. Once any flight references it, its type is protected to preserve qualification meaning across flight history.
- Registration corrections retain the same aircraft identity and flight foreign keys.
- Deactivation rejects SCHEDULED flights whose arrival is after the application clock, covering ongoing and future operations. Resolve or move those flights first. Historical flights permit deactivation.
- Creating or rescheduling a flight requires an active aircraft. Aircraft administration and flight scheduling acquire the same aircraft row lock.

### Qualifications

- A grant requires existing crew and requirement records, matching crew/required roles and an absent crew/requirement pair. Crew locks serialize grants against role changes and other qualification operations.
- An expired qualification remains a valid administrative record. Past dates and null expiry are supported. No flight-specific eligibility engine runs here.
- Assignment history is inferred from crew ID, assigned role and the referenced flight's aircraft type because V1 has no assignment-to-qualification foreign key.
- Revocation is allowed only when that matching assignment history is empty, including cancelled history in the protection.
- Shortening expiry is rejected if it would precede any matching assignment's UTC departure date. Expiry is inclusive on that date. Extending expiry or making it open-ended retains the record.
- These restrictions preserve existing history within V1's representation. V1 records neither qualification change events nor the exact qualification used for an assignment. A complete temporal qualification audit requires a separately approved schema milestone.

### Flights

- New flights start SCHEDULED. Required fields include an existing aircraft, two existing and distinct airports, a flight number and departure/arrival instants. Arrival must follow departure.
- Input timestamps for stored schedules must have at most microsecond precision, matching PostgreSQL. Rejecting finer precision prevents an overlap check and persisted schedule from using different intervals.
- A flight instance is unique by normalized flight number and departure instant, across all aircraft and statuses. Historic creation is supported for course data entry.
- Aircraft occupancy includes SCHEDULED and COMPLETED flights. CANCELLED flights release the interval. Overlap uses `existingDeparture < requestedArrival && existingArrival > requestedDeparture`. Adjacent endpoints are allowed.
- Only SCHEDULED flights can change details. Any actual detail change is rejected while ACTIVE assignments exist. An identical request is an allowed no-op. No assignment is automatically altered or removed.
- SCHEDULED may transition to COMPLETED after its scheduled arrival or to CANCELLED when it has no ACTIVE assignments. COMPLETED and CANCELLED are terminal. Repeating the current status is idempotent.
- Cancellation with ACTIVE assignments returns an explicit conflict. Phase 2.3 must design an atomic assignment-resolution workflow before that limitation can be relaxed.

## Validation and exception handling

`ServiceValidation` runs Jakarta Bean Validation on record inputs and validates IDs, time windows and paging before repository use. It raises `InvalidInputException` with field-oriented messages. Enum inputs are typed Java values and required where applicable. Future adapters must convert malformed external enum strings into input errors.

| Business exception | Meaning for callers |
| --- | --- |
| InvalidInputException | Correct request fields, IDs, time bounds or paging |
| ResourceNotFoundException | A requested record or required reference is absent |
| DuplicateResourceException | A unique identifier or crew/qualification pair already exists |
| BusinessConflictException | Current state, history, scheduling or a competing transaction prevents the operation |
| PersistenceFailureException | The database operation could not complete |

All extend `BusinessException`, with duplicate errors also extending `BusinessConflictException`. Display the sanitized message and log the cause internally. Future HTTP/UI adapters should map these types to their own response contracts. A validation or business failure during a composed write transaction causes rollback of that transaction.

## Transactions and PostgreSQL concurrency

`ServiceTransactions` delegates to a separate Spring-proxied `ServiceUnitOfWork`. Its read methods use `@Transactional(readOnly = true)` and its write methods use `@Transactional`, with default REQUIRED propagation. The outer wrapper translates database exceptions after the proxy returns, covering transaction completion as well as repository calls. PostgreSQL SQLSTATE 23505 becomes a typed duplicate error, other integrity failures become business conflicts, and other database/transaction failures receive sanitized messages. The original cause remains available for internal diagnosis.

Writes validate, acquire locks, recheck state, flush and construct DTOs inside one transaction. Database identity generation and flight audit triggers participate in that transaction. When a caller supplies an outer transaction, service operations join it. That caller owns the final commit and should let failures abort the complete operation. Avoid composing writes inside read-only transactions or making direct entity edits alongside service calls.

`ServiceLocks` uses repository `PESSIMISTIC_WRITE` queries and refreshes each entity while holding its lock. This refresh prevents an entity previously cached in an outer persistence context from masking another transaction's committed change. PostgreSQL emits row-locking statements, observed as `FOR NO KEY UPDATE` with this Hibernate version. Locks last until the encompassing transaction completes.

| Operation | Lock order and protected decision |
| --- | --- |
| Crew update/activity and qualification mutations | Crew row, then validation against qualifications and assignment history |
| Aircraft update/activity | Aircraft row, then referenced-flight checks |
| Flight create | Aircraft row, then active/unique/overlap checks and insert |
| Flight update | Flight row, then distinct old/new aircraft rows in ascending ID order, then checks and update |
| Flight status/cancellation | Flight row, then its aircraft row, then transition/assignment checks |

Every cooperating aircraft scheduler locks the aircraft before inspecting occupied intervals. At PostgreSQL's normal READ COMMITTED isolation, a waiting scheduler rechecks after the winner commits and sees its new flight. New flights on different aircraft can proceed independently. Same-aircraft cancellation and rescheduling hold the relevant aircraft locks through commit. Natural-key uniqueness also remains enforced when competing writers touch different rows.

Phase 2.3 assignment work must follow a compatible order when composing locks. Acquire affected crew rows in ascending ID order, affected flight rows in ascending ID order, then affected aircraft rows in ascending ID order, and revalidate under those locks. Flight update locks both old and new aircraft. Aircraft administration performs ordinary MVCC reads of referenced flights while holding its aircraft lock, avoiding an inverse flight-lock order.

Direct repository writes or external SQL can bypass service business checks. All future scheduling and administration workflows must use these services and the shared locking protocol. Database constraints remain the structural integrity boundary. An overlap exclusion constraint would require a separately approved schema change. This milestone needs no migration or dependency changes. V1/V2 and database-managed FlightAudit are preserved.

## Teammate usage

Obtain reference IDs from the read services, then pass DTO records to the write services. A typical crew preparation flow inside an injected application component is shown below.

```java
CrewView pilot = crewService.create(new CrewRequest(
        "Demo Pilot", CrewRole.PILOT, "TEAM-P-001", 4, "9000000000"));

QualificationView requirement = qualificationService.requirements().stream()
        .filter(value -> value.aircraftTypeId().equals(aircraftTypeId))
        .filter(value -> value.requiredRole() == CrewRole.PILOT)
        .findFirst().orElseThrow();

qualificationService.grant(pilot.id(), new QualificationGrantRequest(
        requirement.id(), LocalDate.of(2027, 12, 31)));

Page<FlightView> schedule = flightService.search(new FlightFilter(
        null, FlightStatus.SCHEDULED, aircraftId, departureFrom, departureUntil),
        PageRequest.of(0, 25));
```

Imports come from `com.aeroroster.service`, `com.aeroroster.service.dto`, the existing enum package, `java.time` and Spring Data paging. Inject services through a constructor. Use a caller transaction when several successful operations must commit together. Returned records are detached values. Fetch again for current state after a later mutation. No operation in this milestone creates duty assignments.

## Tests and local setup

Use the existing dedicated `aeroroster_test` PostgreSQL database and local `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD` values documented in Phase 2.1. A fresh machine needs a one-time database creation by an authorized PostgreSQL account.

```powershell
psql -U postgres -d postgres -c "CREATE DATABASE aeroroster_test;"
mvn clean test
mvn package
git diff --check
```

`PostgresTest` centralizes the existing test datasource properties and guards the database name before datasource/Flyway initialization. Tests fail on an unavailable database. The original persistence tests retain their assertions and use this shared guard. Unit tests verify rejection of development or other database URLs.

Core service integration tests roll back each test. `FixedClockConfiguration` supplies 18 November 2026 at 12:00 UTC for deterministic time policies. Production uses an injected `Clock.systemUTC()` bean.

Concurrency tests require committed rows visible to independent transactions. Each creates a uniquely named test aircraft, operates only on that aircraft, awaits workers and removes only its own flight/audit/aircraft fixtures in `finally`. Seed records are retained. Run one suite at a time against the dedicated database. Identity sequences may advance across rollbacks and cleanup.

The scheduling race test holds an aircraft row lock, starts two requests and observes both waiting in `pg_stat_activity` before releasing the lock. It verifies exactly one flight and one INSERT audit event commit. Another test loads an aircraft in an outer persistence context, commits deactivation on another thread and verifies the refreshed scheduling check rejects the inactive aircraft.

Service tests cover creation, edits, active/inactive filters, duplicate identifiers, invalid inputs/references, role changes, qualification history and expiry boundaries, aircraft state restrictions, flight search, overlap, adjacent endpoints, aircraft moves in either ID order, terminal statuses, cancellation rejection, audit events and translated database uniqueness failures. Unit validation tests check required fields, experience, phone digit counts and lengths independently.

## Verification evidence

Verified on 18 November 2026 with JDK 26.0.2 compiling with Java `release 21`, Maven 3.10.1, Spring Boot 4.1.2 and PostgreSQL 17.11. Execution on a separate Java 21 runtime remains unverified.

| Check | Exact result |
| --- | --- |
| `mvn clean test` | BUILD SUCCESS, exit 0, 59 tests, 0 failures, 0 errors, 0 skipped, completed 16:48:54 IST |
| `mvn package` | BUILD SUCCESS, exit 0, 59 tests, 0 failures, 0 errors, 0 skipped, completed 16:52:15 IST |
| DutyWindowTest | 3 passed |
| PersistenceIntegrationTest | 10 passed |
| CoreServicesIntegrationTest | 30 passed |
| FlightConcurrencyIntegrationTest | 2 passed, including observed PostgreSQL lock waits and cache refresh after concurrent deactivation |
| ServiceValidationTest | 10 passed |
| PostgresDatabaseGuardTest | 4 passed |
| Flyway and Hibernate | Both migrations validated, schema v2 up to date, entity manager initialized with schema validation in tests and the packaged application |
| `git diff --check` | Exit 0 |
| Migration source diff | Exit 0 for `git diff --exit-code -- src/main/resources/db/migration` |
| Manual service workflow | Exit 0, crew activity, qualification grant/expiry/revocation, aircraft registration, flight create/edit, overlap rejection and cancellation passed |
| Manual workflow rollback | All ten table counts restored, including trigger-generated audit rows |
| Packaged-JAR HTTP smoke | Confirmed new process startup on port 18081 at 16:54:19 IST, homepage HTTP 200 and CSS HTTP 200, verification process stopped |

The manual service workflow bootstrapped the application with the packaged dependencies, targeted the guarded test database and wrapped its operations in an explicitly rolled-back transaction. It ran from the ignored `target/Phase22Smoke.java` verification harness. Local evidence is retained in `phase22-test.log`, `target/phase22-package.log`, `target/phase22-manual.log`, `target/phase22-runtime.log`, `target/phase22-runtime-error.log` and `target/surefire-reports`. The executable artifact is `target/aeroroster-0.1.0-SNAPSHOT.jar`.

The packaged JAR also validated the actual development schema. Read-only development checks confirmed V1/V2 success and the seed counts below. The manual test-database workflow restored the same counts after rollback.

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

Successful Maven runs emitted optional repository metadata HTTP 401 warnings and JVM dynamic-agent/class-sharing warnings. They did not fail compilation, tests or packaging. Git emitted a line-ending conversion notice for the roadmap. There are no unresolved Phase 2.2 implementation failures.

No manual setup is required on the current machine. Fresh machines require the test-database and credential setup described above. Explicit teacher approval remains an owner-managed item in the existing Phase 1 checklist.

## Changed-file inventory

Created under `src/main/java/com/aeroroster/service/`.

```text
AircraftService.java
AircraftTypeService.java
AirportService.java
CrewService.java
FlightService.java
QualificationService.java
ServiceConfiguration.java
ServiceLocks.java
ServiceTransactions.java
ServiceUnitOfWork.java
ServiceViews.java
dto/AircraftFilter.java
dto/AircraftRequest.java
dto/AircraftTypeView.java
dto/AircraftView.java
dto/AirportView.java
dto/AssignmentView.java
dto/CrewFilter.java
dto/CrewQualificationView.java
dto/CrewRequest.java
dto/CrewView.java
dto/FlightFilter.java
dto/FlightRequest.java
dto/FlightView.java
dto/QualificationExpiryRequest.java
dto/QualificationGrantRequest.java
dto/QualificationView.java
exception/BusinessConflictException.java
exception/BusinessException.java
exception/DuplicateResourceException.java
exception/InvalidInputException.java
exception/PersistenceFailureException.java
exception/ResourceNotFoundException.java
validation/InputValues.java
validation/ServiceValidation.java
```

Also created.

```text
docs/07_PHASE22_SERVICES.md
src/test/java/com/aeroroster/service/CoreServicesIntegrationTest.java
src/test/java/com/aeroroster/service/FlightConcurrencyIntegrationTest.java
src/test/java/com/aeroroster/service/ServiceValidationTest.java
src/test/java/com/aeroroster/support/FixedClockConfiguration.java
src/test/java/com/aeroroster/support/PostgresDatabaseGuardTest.java
src/test/java/com/aeroroster/support/PostgresTest.java
```

Modified existing files.

```text
README.md
docs/03_RULES_AND_DECISIONS.md
docs/04_PHASE_ROADMAP.md
src/main/java/com/aeroroster/persistence/repository/AircraftRepository.java
src/main/java/com/aeroroster/persistence/repository/AirportRepository.java
src/main/java/com/aeroroster/persistence/repository/CrewRepository.java
src/main/java/com/aeroroster/persistence/repository/DutyAssignmentRepository.java
src/main/java/com/aeroroster/persistence/repository/FlightRepository.java
src/test/java/com/aeroroster/persistence/PersistenceIntegrationTest.java
```

## Acceptance recommendation

Phase 2.2 is ready for team review and acceptance. The core service contracts, validation, history safeguards, transactions, PostgreSQL concurrency coverage, migration validation, manual workflow and handoff documentation are complete. The next milestone remains subject to its own review and approval.

## Phase 2.3 handoff

The assignment engine must check active crew, role and date-valid qualification, unavailability, crew overlap, illustrative duty hours and rest settings within the shared transaction and lock protocol. It must also define atomic unassignment and cancellation resolution. The flight and crew services currently return explicit conflicts for unsafe administrative operations.

Assignment history retains role and duty-window snapshots in V1. FlightAudit records operation, time, flight ID and flight number, and provides a limited change history. More detailed qualification or schedule auditing requires explicit design and schema approval. Monetary calculations, if introduced later, must use BigDecimal.

Phase 2.2 completion does not approve Phase 2.3 implementation. Review this milestone before starting that work.
