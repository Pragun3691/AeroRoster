# Roadmap with gates

## Phase 1 — Foundation / schema / bootstrap
Deliver: scope, source audit, decisions, 10-table schema, demo seed, Spring Boot skeleton, initial domain test. Gate: `mvn test`, `mvn spring-boot:run`, Flyway V1/V2 on empty PostgreSQL, homepage visible, seed counts checked.

## Phase 2 — Core backend
Deliver: JPA entities and repositories; Crew/Airport/AircraftType/Aircraft/Flight/Qualification CRUD; DTO validation; transactionally safe AssignmentService; unit and PostgreSQL integration tests. Gate: valid assignment commits; each invalid case rejects without side effects; concurrency checks.

- Phase 2.1 persistence is committed at `c4b65d4`.
- Phase 2.2 core services implement validated administration and transaction-safe aircraft scheduling. Evidence and review status are in `07_PHASE22_SERVICES.md`.
- Phase 2.3 assignment services were authorized after the Phase 2.2 commit. Scheduling implementation and acceptance evidence are in `08_PHASE23_SCHEDULING.md`.
- Phase 2.4 exposes core/scheduling services through a versioned local REST API. API contracts and acceptance evidence are in `09_PHASE24_REST_API.md`.
- Phase 2.5 reviews the integrated backend, fills regression gaps and records reliability/security evidence in `10_PHASE25_BACKEND_VERIFICATION.md`.
- Phase 2.6 closes stale administrative edits, freezes the existing API and prepares the three-member handoff in `11_PHASE26_TEAM_HANDOFF.md`. Commit/review/merge approval is an owner-managed gate before teammates branch from the verified main baseline.

## Phase 3 — UI workflows
Deliver: integrated dashboard, search/filter forms, crew and flight detail/edit screens, staffing and conflict explanation, error states, schedule views. Gate: complete browser-based admin journey.

## Phase 4 — Standout features
Deliver: candidate ranking based on eligibility + workload, guided allocation, reporting/export, calendars, audit views. Gate: explainable ranked recommendations; no auto-commit without admin confirmation.

## Phase 5 — Final verification and submission
Deliver: full automated tests, clean sample data and setup guide, README, ERD/UML, presentation, 5–7 minute demo script, source ZIP. Gate: new database setup succeeds and every core workflow demonstrated.
