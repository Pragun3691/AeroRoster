# AeroRoster — Airline Crew Management & Duty Scheduling

Phase 1 foundation is committed at `a6efdb6`. Phase 2.1 implements and verifies the JPA persistence layer. See the current evidence and test setup in the implementation notes. [Phase 2.1 notes](docs/06_PHASE21_PERSISTENCE.md)

Source DBMS project: https://github.com/Pragun3691/airline-crew-dbms

## Objective

Create a Java-based airline operations application for managing airports, aircraft, crew, qualifications, flights, unavailability, and duty assignments. Prevent invalid assignments (overlap, qualifications, rest, duty hours); later support ranked crew suggestions, schedules and reports.

## Locked technology choices (unless course restrictions say otherwise)

- Java 21, Spring Boot 4.1.2, Maven
- Spring MVC + Thymeleaf templates
- Spring Data JPA, PostgreSQL, Flyway migrations
- JUnit 5 / Spring Boot testing

## Setup on Windows PowerShell

1. Install Java 21 (JDK), Maven 3.9+, PostgreSQL 16+.
2. Create **a separate new** database, not the previous DBMS database:
   ```sql
   CREATE DATABASE aeroroster_dev;
   ```
   Execute this as a user permitted to create databases (e.g., in psql). If already created, skip.
3. Open PowerShell at this project root and set your **local** PostgreSQL credentials:
   ```powershell
   $env:DB_URL = "jdbc:postgresql://localhost:5432/aeroroster_dev"
   $env:DB_USER = "postgres"
   $env:DB_PASSWORD = "YOUR_LOCAL_PASSWORD"
   ```
   Never commit or share passwords. Prefer a dedicated non-superuser database account for longer-term use.
4. Verify Java/Maven:
   ```powershell
   java -version
   mvn -version
   ```
5. Start the Java app:
   ```powershell
   mvn spring-boot:run
   ```
   Flyway should apply `V1` and `V2` on startup. Go to `http://localhost:8080`.
6. Confirm tables via psql:
   ```sql
   \c aeroroster_dev
   \dt
   SELECT count(*) FROM crew;
   SELECT count(*) FROM flight;
   SELECT * FROM flyway_schema_history ORDER BY installed_rank;
   ```
7. Run Java tests:
   ```powershell
   mvn test
   ```

Phase 1 completion was reported by the project owner. PostgreSQL migration history and seed records were independently checked during Phase 2.1. Integration tests require the separate `aeroroster_test` database described in the [test instructions](docs/06_PHASE21_PERSISTENCE.md#test-setup).

## Repo layout

```
AeroRoster-Phase1/
├── AGENTS.md
├── docs/
│   ├── 01_SCOPE.md
│   ├── 02_SOURCE_AUDIT.md
│   ├── 03_RULES_AND_DECISIONS.md
│   ├── 04_PHASE_ROADMAP.md
│   └── 05_PHASE1_CHECKLIST.md
├── pom.xml
└── src/
    ├── main/java/com/aeroroster/
    ├── main/resources/db/migration/
    ├── main/resources/templates/
    └── test/java/com/aeroroster/
```

## Important guardrails

- **Educational simulation**: the suggested duty-hour and rest limits are project settings and are *not* certification of compliance with DGCA, FAA or other aviation regulation.
- Flight date-times stored as UTC-aware instants (`TIMESTAMPTZ`); UI can display selected local time zones.
- All crew assignment validation belongs in Java services within a transaction. PostgreSQL constraints enforce primary keys, foreign keys, uniqueness and basic checks.
- Do not deploy this initial local-only build publicly; authentication/authorization is not yet implemented.
- SQL migrations belong to Flyway; do not enable Hibernate schema generation.
- Preserve original Oracle archive; this project is a separate codebase.
