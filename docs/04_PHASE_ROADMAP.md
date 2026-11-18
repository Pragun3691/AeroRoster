# Roadmap with gates

## Phase 1 — Foundation / schema / bootstrap
Deliver: scope, source audit, decisions, 10-table schema, demo seed, Spring Boot skeleton, initial domain test. Gate: `mvn test`, `mvn spring-boot:run`, Flyway V1/V2 on empty PostgreSQL, homepage visible, seed counts checked.

## Phase 2 — Core backend
Deliver: JPA entities and repositories; Crew/Airport/AircraftType/Aircraft/Flight/Qualification CRUD; DTO validation; transactionally safe AssignmentService; unit and PostgreSQL integration tests. Gate: valid assignment commits; each invalid case rejects without side effects; concurrency checks.

## Phase 3 — UI workflows
Deliver: integrated dashboard, search/filter forms, crew and flight detail/edit screens, staffing and conflict explanation, error states, schedule views. Gate: complete browser-based admin journey.

## Phase 4 — Standout features
Deliver: candidate ranking based on eligibility + workload, guided allocation, reporting/export, calendars, audit views. Gate: explainable ranked recommendations; no auto-commit without admin confirmation.

## Phase 5 — Final verification and submission
Deliver: full automated tests, clean sample data and setup guide, README, ERD/UML, presentation, 5–7 minute demo script, source ZIP. Gate: new database setup succeeds and every core workflow demonstrated.
