# Phase 2.4 REST API

## Inspection and implementation plan

Phases 2.1, 2.2 and 2.3 are committed at c4b65d4, 0c8f897 and 4bfe6c2. The working tree was clean before this milestone. Inspection covered the project rules, docs 01–08, existing service/DTO/exception contracts, persistence mappings and queries, migrations and tests.

Expose the current services through thin versioned controllers under /api/v1. Reuse service request/output records and add small transport records for activity/status changes, pagination and errors. Validate transport syntax, IDs, paging, enums and timestamps at the HTTP boundary. Keep scheduling rules and transactions in the services. Add database-backed paged overloads for growing history/subresource lists while retaining existing callers.

Provide a stable error record through RestControllerAdvice, strict JSON request handling and same-origin integration. Preserve applied migrations and database-managed auditing. Document this unauthenticated API as local-development-only, bind locally by default, and provide OpenAPI plus a local documentation viewer without external runtime assets.

Risks include malformed requests, mass assignment of managed fields, inconsistent errors, stale eligibility previews and concurrent HTTP scheduling requests. Test controller boundaries, real PostgreSQL workflows and persisted state, concurrent requests using independent connections, OpenAPI/controller agreement, existing tests, migration/schema validation, packaged HTTP endpoints and rollback/fixture cleanup. Run mvn clean test, mvn package and git diff --check.

## Endpoint inventory

| Method and path | Query parameters | Request | Response | Success |
| --- | --- | --- | --- | --- |
| `POST /api/v1/crew` |  | CrewRequest | CrewView | 201 |
| `GET /api/v1/crew` | q, role, active, page, size | None | CrewPage | 200 |
| `GET /api/v1/crew/{crewId}` |  | None | CrewView | 200 |
| `PUT /api/v1/crew/{crewId}` |  | CrewRequest | CrewView | 200 |
| `PATCH /api/v1/crew/{crewId}/activity` |  | ActivityRequest | CrewView | 200 |
| `GET /api/v1/crew/{crewId}/assignments` | activeOnly, page, size | None | AssignmentPage | 200 |
| `GET /api/v1/airports` | q | None | AirportView[] | 200 |
| `GET /api/v1/airports/{airportId}` |  | None | AirportView | 200 |
| `GET /api/v1/aircraft-types` |  | None | AircraftTypeView[] | 200 |
| `GET /api/v1/aircraft-types/{aircraftTypeId}` |  | None | AircraftTypeView | 200 |
| `GET /api/v1/aircraft-types/{aircraftTypeId}/qualifications` |  | None | QualificationView[] | 200 |
| `GET /api/v1/qualifications` |  | None | QualificationView[] | 200 |
| `POST /api/v1/aircraft` |  | AircraftRequest | AircraftView | 201 |
| `GET /api/v1/aircraft` | q, active, aircraftTypeId, page, size | None | AircraftPage | 200 |
| `GET /api/v1/aircraft/{aircraftId}` |  | None | AircraftView | 200 |
| `PUT /api/v1/aircraft/{aircraftId}` |  | AircraftRequest | AircraftView | 200 |
| `PATCH /api/v1/aircraft/{aircraftId}/activity` |  | ActivityRequest | AircraftView | 200 |
| `GET /api/v1/aircraft/{aircraftId}/flights` | page, size | None | FlightPage | 200 |
| `GET /api/v1/crew/{crewId}/qualifications` |  | None | CrewQualificationView[] | 200 |
| `GET /api/v1/crew/{crewId}/qualifications/{qualificationId}` |  | None | CrewQualificationView | 200 |
| `POST /api/v1/crew/{crewId}/qualifications` |  | QualificationGrantRequest | CrewQualificationView | 201 |
| `PUT /api/v1/crew/{crewId}/qualifications/{qualificationId}/expiry` |  | ExpiryUpdateRequest | CrewQualificationView | 200 |
| `DELETE /api/v1/crew/{crewId}/qualifications/{qualificationId}` |  | None | None | 204 |
| `POST /api/v1/flights` |  | FlightRequest | FlightView | 201 |
| `GET /api/v1/flights` | flightNumber, status, aircraftId, departureFrom, departureUntil, page, size | None | FlightPage | 200 |
| `GET /api/v1/flights/{flightId}` |  | None | FlightView | 200 |
| `PUT /api/v1/flights/{flightId}` |  | FlightRequest | FlightView | 200 |
| `PATCH /api/v1/flights/{flightId}/status` |  | FlightStatusRequest | FlightView | 200 |
| `PUT /api/v1/flights/{flightId}/cancellation` |  | EmptyRequest | FlightView | 200 |
| `GET /api/v1/flights/{flightId}/assignments` | activeOnly, page, size | None | AssignmentPage | 200 |
| `GET /api/v1/flights/{flightId}/coverage` |  | None | FlightCrewCoverage | 200 |
| `POST /api/v1/assignments/eligibility` |  | AssignmentRequest | EligibilityDecision | 200 |
| `POST /api/v1/assignments` |  | AssignmentRequest | AssignmentView | 201 |
| `GET /api/v1/assignments/{assignmentId}` |  | None | AssignmentView | 200 |
| `PUT /api/v1/assignments/{assignmentId}/cancellation` |  | EmptyRequest | AssignmentView | 200 |
| `POST /api/v1/assignments/{assignmentId}/reassignments` |  | EmptyRequest | AssignmentView | 201 |
| `POST /api/v1/crew/{crewId}/unavailability` |  | UnavailabilityRequest | UnavailabilityView | 201 |
| `GET /api/v1/crew/{crewId}/unavailability` | page, size | None | UnavailabilityPage | 200 |
| `GET /api/v1/crew/{crewId}/unavailability/{periodId}` |  | None | UnavailabilityView | 200 |
| `PUT /api/v1/crew/{crewId}/unavailability/{periodId}` |  | UnavailabilityRequest | UnavailabilityView | 200 |
| `DELETE /api/v1/crew/{crewId}/unavailability/{periodId}` |  | None | None | 204 |
| `GET /api/v1/crew/{crewId}/unavailability/conflicts` | start, end | None | UnavailabilityConflictView | 200 |

## Local access and security

The API has no authentication or authorization. It is for local development only and must not be publicly deployed as a production-ready service. The default bind address is 127.0.0.1. SERVER_ADDRESS can change the address for an explicitly configured environment, but does not add access control.

Start the app with the existing local DB_URL, DB_USER and DB_PASSWORD environment settings, then open the local documentation viewer and specification.

```powershell
mvn spring-boot:run
```

- API base is `http://127.0.0.1:8080/api/v1`.
- Documentation viewer is `http://127.0.0.1:8080/api-docs/index.html`.
- OpenAPI 3.1 JSON is `http://127.0.0.1:8080/api/v1/openapi.json`.

The viewer loads only local assets and the same maintained specification used by the contract tests. Its searchable endpoint list includes request/response definitions and the complete schema catalogue. Update the JSON specification alongside controller or DTO changes. Contract tests check all operation paths/methods, record fields, enum values and schema references.

API requests with an Origin header must match the request's scheme, host and port. Cross-origin requests and preflights receive a JSON 403 with ORIGIN_NOT_ALLOWED. Responses do not add permissive Access-Control-Allow-Origin headers. Requests without an Origin header, such as command-line clients, are accepted. This is a browser-origin policy and provides no authentication guarantee.

Member 2 should serve the frontend from AeroRoster's origin or configure a development proxy that presents a matching origin to the backend. Use relative API URLs. Opening a frontend from a file URL or another development-server origin directly against this API will be rejected. Changing localhost to 127.0.0.1 also changes the origin, so use one hostname consistently.

## HTTP and JSON conventions

- Resource objects reuse the existing service output records. Growing collections return PageResponse. The fixed, read-only airport/type/requirement catalogue and a crew member's held qualifications return arrays.
- Creation returns 201, a resource body and a canonical relative Location header. Reads and updates return 200. Qualification revocation and unavailability removal return 204 with no body. Assignment and flight cancellation use idempotent PUT operations and retain the existing business behavior.
- Send Content-Type application/json for request bodies and Accept application/json. An optional cancellation/reassignment body may be absent or `{}`. It cannot carry replacement crew IDs or other fields.
- IDs are positive int64 JSON integers. Enums use their exact uppercase names. Numeric enum ordinals, fractional integer values and string-to-number/boolean coercion are rejected.
- Instant inputs are ISO-8601 strings with Z or an explicit UTC offset. Numeric timestamps, arrays and offset-free local timestamps are rejected. Responses normalize instants to UTC Z. Stored schedule/unavailability times retain the existing maximum microsecond precision rule.
- LocalDate values use ISO dates such as `2027-12-31`. Qualification expiry may be null. ISO-8601 strings such as PT10H represent durations in eligibility policy/load output.
- Unknown JSON properties are rejected, including server-managed IDs, activity/status fields outside their dedicated operations, assignment windows and creation timestamps. Build write bodies from the documented input fields rather than copying a GET response unchanged.

## Request schemas

The OpenAPI components provide machine-readable constraints. All request objects reject additional properties.

| Schema | Fields and constraints |
| --- | --- |
| CrewRequest | Required name up to 100 characters, CrewRole, licenseNo up to 30, nonnegative integer experienceYears and contactNo up to 20 characters. Contact accepts 7–15 digits and the existing permitted formatting. Strings are stripped and licenses normalized by the service DTO. |
| AircraftRequest | Required registration up to 24 characters and positive aircraftTypeId. Registrations are normalized and unique. |
| FlightRequest | Required flightNumber up to 15 characters, positive sourceAirportId/destinationAirportId/aircraftId and departureTime/arrivalTime. The service validates references, ordering, aircraft state and occupancy. |
| ActivityRequest | Required non-null boolean active. |
| FlightStatusRequest | Required SCHEDULED, COMPLETED or CANCELLED status. The service enforces transitions. |
| QualificationGrantRequest | Required positive qualificationId, optional nullable validUntil. Omitted/null expiry means open-ended. |
| ExpiryUpdateRequest | validUntil must be present. An ISO date changes expiry and explicit null makes it open-ended. An empty object is rejected. |
| AssignmentRequest | Required positive crewId and flightId, plus assignedRole. Duty endpoints and status are managed by the service. |
| UnavailabilityRequest | Required startTime, endTime and nonblank reason up to 200 characters. |
| EmptyRequest | An optional empty object for cancellation/reassignment actions. |

The qualificationId path identifies the qualification requirement, not CrewQualificationView.id, which identifies the held link. Grant responses include both identifiers and their Location addresses the crew/requirement pair. Unavailability IDs are scoped to the crew in the path, and another crew cannot edit/remove that period.

## Pagination and filters

Growing collections use zero-based page and size parameters. Defaults are page 0 and size 25, with size bounded to 1–200. Ordering is fixed by the services. Unknown custom sort parameters do not select entity properties. The response format is shown below.

```json
{
  "items": [],
  "page": 0,
  "size": 25,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

Crew, aircraft and flight searches use the existing database-backed service paging. Assignment history/active lists, aircraft flights and unavailability now have additive pageable service overloads backed by repository Page queries. Existing List-returning callers are preserved. Read graphs fetch the relationships needed for paged flight/assignment DTOs.

Crew search accepts q, role and active. Aircraft search accepts q, active and aircraftTypeId. Flight search accepts flightNumber, status, aircraftId, departureFrom and departureUntil. Text filters are case-insensitive literal substrings, with percent/underscore escaping supplied by the existing services. Search text is bounded to 200 characters at the HTTP boundary.

Flight ranges filter departure time using `[departureFrom, departureUntil)`, and either bound may be omitted. Encode plus signs in offset query timestamps with URLSearchParams or equivalent. Crew assignment lists default to complete history, while flight assignment lists default to active rows. Set activeOnly explicitly to true/false to choose the other view. Reference arrays are bounded by the current small read-only catalogue.

## Error contract

Handled API failures return application/json with the same record shape. Timestamp uses the injected UTC clock, path excludes query values, and fieldErrors contains safe field/message pairs without rejected values. eligibility is null except for an assignment-rule rejection.

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "timestamp": "2026-11-18T12:00:00Z",
  "path": "/api/v1/crew",
  "fieldErrors": [{"field": "name", "message": "must not be blank"}],
  "eligibility": null
}
```

| HTTP | Error code | Meaning |
| --- | --- | --- |
| 400 | VALIDATION_ERROR | DTO or service input rules failed |
| 400 | INVALID_PARAMETER | Query/path conversion, format or paging failed |
| 400 | MALFORMED_JSON | Missing/malformed body, unknown fields, invalid enum or JSON value types |
| 403 | ORIGIN_NOT_ALLOWED | Use a matching origin |
| 404 | NOT_FOUND | Resource/reference or route is absent |
| 409 | DUPLICATE_RESOURCE | Unique identifier or qualification already exists |
| 409 | BUSINESS_CONFLICT | State, scheduling or history prevents an operation |
| 409 | ASSIGNMENT_REJECTED | Current scheduling rules rejected assignment; eligibility contains all practical reasons |
| 409 | DOMAIN_CONFLICT | Fallback for another typed domain failure |
| 405 | METHOD_NOT_ALLOWED | Method unsupported; Allow lists supported methods |
| 406 | NOT_ACCEPTABLE | API output is application/json |
| 415 | UNSUPPORTED_MEDIA_TYPE | Use application/json for the body |
| 500 | INTERNAL_ERROR | Unexpected or persistence failure; public message is sanitized |

ApiExceptionHandler uses RestControllerAdvice. It maps existing typed service exceptions, request binding/validation exceptions and framework resource/method/media exceptions. Original unexpected exceptions are logged internally. Client responses omit SQL diagnostics, credentials, stack traces, rejected payload values and entity internals. The origin filter uses the same error factory for 403 responses.

## Relevant business behavior

Controllers translate HTTP inputs and invoke services. Domain rules remain in the core/scheduling services.

- Crew and aircraft start active. Crew deactivation protects ongoing/future active duties, and aircraft deactivation protects ongoing/future scheduled flights.
- Aircraft registration and crew licenses are normalized and unique. Aircraft type changes protect referenced flight history. Airport and aircraft-type writes are absent.
- Qualification grants require compatible roles. Expired qualifications remain valid administrative records. Revocation and expiry changes retain existing history protections.
- Flight instances are unique by flight number and departure instant. Active aircraft, distinct airports, valid references, half-open aircraft occupancy and status transitions are enforced by FlightService. Active assignments prevent unsafe edits and cancellation.
- Scheduling uses the existing crew → flight → aircraft locks and writable READ_COMMITTED transactions. REST calls do not implement their own conflict checks or transactions.
- Eligibility preview returns 200 even when ineligible, including missing-crew/flight reasons. Every assignment/reassignment performs its own locked recheck. A 409 ASSIGNMENT_REJECTED response includes the current EligibilityDecision.
- SchedulingReason uses code and explanation, retaining the Phase 2.3 model. Display policyNotice with eligibility/coverage because the limits are illustrative academic settings.
- Cancellation retains assignment history. Reassignment uses the cancelled row's crew, flight and role and returns a fresh assignment ID. Coverage reports missing role counts and staffing independently of flight status.

## Examples for Member 2

Create a crew member using the request DTO fields. The returned Location can be fetched to retrieve the created resource.

```http
POST /api/v1/crew
Content-Type: application/json
Accept: application/json

{"name":"Demo Pilot","role":"PILOT","licenseNo":"TEAM-P-001","experienceYears":4,"contactNo":"9000000000"}
```

```json
{"id":101,"name":"Demo Pilot","role":"PILOT","licenseNo":"TEAM-P-001","experienceYears":4,"contactNo":"9000000000","active":true}
```

IDs in examples are illustrative. Use returned IDs and the reference endpoints for actual requests. Update editable fields with PUT /crew/{crewId}, and change activity separately with PATCH /crew/{crewId}/activity using `{"active":false}`.

```javascript
async function loadCrew(page = 0) {
  const query = new URLSearchParams({page, size: 25, active: true});
  const response = await fetch(`/api/v1/crew?${query}`, {
    headers: {Accept: "application/json"}
  });
  const result = await response.json();
  if (!response.ok) {
    // Display result.message and any result.fieldErrors in the form.
    throw new Error(result.message);
  }
  return result;
}
```

For writes, JSON.stringify the documented request object and set Content-Type application/json. A 204 response has no JSON body. Keep path/query IDs separate from editable form fields. Same-origin fetch requires no CORS configuration.

## Eligibility and coverage for Member 3

```http
POST /api/v1/assignments/eligibility
Content-Type: application/json

{"crewId":101,"flightId":201,"assignedRole":"PILOT"}
```

The response is EligibilityDecision with eligible, dutyWindow, dutyLoad, reasons, policy and policyNotice. An ineligible reasons entry uses the actual shape below.

```json
{"code":"CREW_TIME_CONFLICT","explanation":"Crew already has an overlapping active duty"}
```

Use the same request with POST /api/v1/assignments after user confirmation. Handle 409 ASSIGNMENT_REJECTED by displaying error.eligibility.reasons from that write. GET /flights/{flightId}/coverage returns required/active/missing counts for each role and minimumStaffingSatisfied. Eligibility and coverage support the future candidate workflow, while allocation/ranking remains a later milestone.

Cancel an assignment with PUT /assignments/{assignmentId}/cancellation. POST /assignments/{assignmentId}/reassignments creates a fresh row from cancelled history. Query activeOnly=false on flight assignments to inspect retained rows. Cancel active assignments before requesting flight cancellation. A competing assignment can still make that cancellation reject safely.

## Development workflow and database safety

Use the existing aeroroster_dev setup from README and guarded aeroroster_test setup from Phase 2.1. Credentials stay in local environment variables. A fresh machine needs the dedicated test database created once by an authorized PostgreSQL account. The test database URL is checked before Flyway startup.

```powershell
mvn clean test
mvn package
git diff --check
java -jar target/aeroroster-0.1.0-SNAPSHOT.jar
```

Tests include controller-only error sanitization/validation, PostgreSQL-backed API workflows and independent real-HTTP scheduling races on a random loopback port. The shared PostgresTest annotation now supports an optional webEnvironment attribute while preserving the existing default mock environment. Transactional API tests roll back. HTTP race fixtures use unique names and exact-ID cleanup after worker/client shutdown, retaining seeded records.

Service changes are additive pageable/read overloads and their repository queries. Existing business rules, locking protocols and old signatures are retained. Applied V1/V2 migrations and dependencies are preserved. Test new HTTP failures against their error bodies and final database state. Keep the OpenAPI JSON and endpoint/schema tables synchronized when editing controllers or DTOs.

## Verification evidence

Verified on 18 November 2026 using JDK 26.0.2 with Java release 21 compilation, Maven 3.10.1, Spring Boot 4.1.2 and PostgreSQL 17.11. A separate Java 21 runtime execution remains unverified.

| Check | Exact result |
| --- | --- |
| mvn clean test | BUILD SUCCESS, exit 0, 165 tests, 0 failures, 0 errors, 0 skipped, completed 18:29:21 IST |
| mvn package | BUILD SUCCESS, exit 0, 165 tests, 0 failures, 0 errors, 0 skipped, completed 18:30:19 IST |
| Existing Phase 1/2.1/2.2/2.3 tests | All 118 passed |
| ApiControllerTest | 6 passed |
| ApiIntegrationTest | 36 passed |
| ApiHttpConcurrencyTest | 2 passed, real HTTP and independent PostgreSQL transactions |
| OpenApiContractTest | 3 passed, checking 42 operations and the DTO/enum/schema contracts |
| Packaged HTTP smoke | 17 checks passed, exit 0 |
| Documentation JavaScript | node --check passed, exit 0 |
| git diff --check | Passed, exit 0 |
| Applied migrations and dependencies | git diff --exit-code for migrations and pom.xml passed |

The packaged JAR confirmed startup at 18:32:44 IST on loopback port 18081. Flyway validated both applied migrations and found schema v2 up to date. Hibernate schema validation initialized successfully. The verification process was stopped afterward.

The manual HTTP checks exercised the homepage, airport reference data, paginated crew/flights, staffing coverage, eligibility preview, not-found/invalid-enum/malformed-JSON/media-negotiation errors, matching-origin access, rejected cross-origin GET/preflight, OpenAPI JSON, the documentation viewer/JavaScript and existing CSS. Expected statuses were verified as 200, 400, 403, 404 and 406 with safe error record shapes. The eligibility preview correctly returned 200 with CREW_NOT_FOUND and FLIGHT_NOT_FOUND for missing IDs. No valid modifying operation was sent to the development database during the manual smoke check.

The real-HTTP test fixtures separately exercised committed create/grant/register/create-flight operations and concurrent POST /assignments requests. Both races observed the database lock waits, required exactly one HTTP 201 and one HTTP 409 with the expected eligibility reason, and verified one final active assignment or pilot slot in PostgreSQL before cleanup. Transactional API tests covered full edit/cancellation/revocation/unavailability workflows and verified rejected writes left no partial rows.

Development table counts before and after the packaged HTTP verification matched the counts below. V1/V2 history entries remained successful. The test database retained seed records, and queries for H24 real-HTTP and A24 transactional fixture prefixes returned zero remaining crew, aircraft and flight rows after the final runs.

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

Ignored local evidence is in phase24-test.log, phase24-api-tests.log, phase24-http-tests.log, phase24-contract-tests.log, target/phase24-package.log, target/phase24-smoke.log, target/phase24-runtime.log, target/phase24-runtime-error.log and target/surefire-reports. The smoke runner is target/phase24-http-smoke.py. The executable artifact is target/aeroroster-0.1.0-SNAPSHOT.jar.

Successful builds emitted optional repository metadata HTTP 401 warnings and JVM dynamic-agent/class-sharing warnings. Git emitted line-ending conversion notices. No implementation test failures or unresolved API failures remain. No manual setup is required on the current machine. Fresh machines require the existing PostgreSQL test-database setup and local credentials.

## Created and modified files

Created Java files under src/main/java/com/aeroroster/web/api.

```text
AircraftController.java
AssignmentController.java
CrewController.java
FlightController.java
QualificationController.java
ReferenceController.java
UnavailabilityController.java
ApiCorsFilter.java
ApiErrors.java
ApiExceptionHandler.java
ApiJsonConfiguration.java
ApiParameterException.java
ApiParameters.java
dto/ActivityRequest.java
dto/ApiError.java
dto/ApiFieldError.java
dto/EmptyRequest.java
dto/ExpiryUpdateRequest.java
dto/FlightStatusRequest.java
dto/PageResponse.java
dto/UnavailabilityConflictView.java
```

Also created.

```text
docs/09_PHASE24_REST_API.md
src/main/resources/static/api/v1/openapi.json
src/main/resources/static/api-docs/index.html
src/main/resources/static/api-docs/docs.js
src/test/java/com/aeroroster/web/api/ApiControllerTest.java
src/test/java/com/aeroroster/web/api/ApiIntegrationTest.java
src/test/java/com/aeroroster/web/api/ApiHttpConcurrencyTest.java
src/test/java/com/aeroroster/web/api/OpenApiContractTest.java
```

Modified existing files.

```text
README.md
docs/03_RULES_AND_DECISIONS.md
docs/04_PHASE_ROADMAP.md
src/main/java/com/aeroroster/persistence/repository/CrewUnavailabilityRepository.java
src/main/java/com/aeroroster/persistence/repository/DutyAssignmentRepository.java
src/main/java/com/aeroroster/persistence/repository/FlightRepository.java
src/main/java/com/aeroroster/service/CrewUnavailabilityService.java
src/main/java/com/aeroroster/service/DutyAssignmentService.java
src/main/java/com/aeroroster/service/FlightService.java
src/main/java/com/aeroroster/service/QualificationService.java
src/main/resources/application.properties
src/test/java/com/aeroroster/support/PostgresTest.java
```

## Limitations

Authentication/authorization, production security controls and public deployment readiness remain future work. Same-origin checks are not identity checks. The read-only reference catalogue uses arrays and would need paged query contracts if the project expands it substantially. There are no bulk write endpoints. Existing academic scheduling, audit-history and roster revalidation limitations from Phase 2.3 continue to apply.

## Acceptance recommendation

Phase 2.4 is ready for team review and acceptance as a local-development API. All 42 operations, stable transport contracts, error handling, validation, PostgreSQL HTTP races, OpenAPI documentation, packaged HTTP checks and existing scheduling regressions have been verified. Subsequent milestones require their own review and authorization.
