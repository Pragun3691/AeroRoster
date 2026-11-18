# Phase 1 checklist

- [x] Project objective and technology choices drafted
- [x] Public Oracle SQL repository reviewed
- [x] Architecture and scheduling rules documented
- [x] Separate starter folder prepared and subsequently committed
- [x] Initial versioned schema and seed scripts authored
- [x] Minimal Spring Boot app / template / Java domain record and tests authored
- [ ] Confirm course permits Spring Boot / web project
- [x] Confirm Java 21 target, Maven, PostgreSQL on local Windows machine (JDK 26.0.2, Maven 3.10.1, PostgreSQL 17.11)
- [x] Create aeroroster_dev database; run Flyway V1 + V2 via application startup (existing successful migration history verified)
- [x] Verify ten tables and seed counts
- [x] Run `mvn test` and verify the homepage (13 passing tests, rebuilt JAR served HTTP 200 on dedicated port 18081)
- [x] Commit as `chore: bootstrap aeroroster phase 1` (`a6efdb6`)
- [x] Project owner explicitly requested Phase 2.1 implementation. Later milestones retain their approval gates.

Phase 1 completion was reported by the project owner. Phase 2.1 independently verifies runtime and database evidence in `06_PHASE21_PERSISTENCE.md`. Explicit teacher approval remains undocumented in this repository. The owner supplied Spring Boot as the stack for the approved Phase 2.1 request.
