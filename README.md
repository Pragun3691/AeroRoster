# AeroRoster — Airline Crew Management & Duty Scheduling

AeroRoster is an enterprise-grade full-stack airline operations and duty roster scheduling system designed to manage airports, aircraft fleets, flight crew profiles, type qualifications, flight schedules, crew unavailability, and duty assignments. The application enforces aviation scheduling constraints—including aircraft type qualifications, mandatory rest periods, maximum duty durations, and overlap prevention—using transactional locking and optimistic concurrency controls.

- **Source DBMS Project:** https://github.com/Pragun3691/airline-crew-dbms
- **API Version:** 1.1.0 frozen contract (42 operations across `/api/v1`)
- **Architecture Baseline:** Spring Boot 4.1.2 REST API + React 18 SPA + PostgreSQL 17 + Flyway

---

## 1. Project Overview and Purpose

AeroRoster solves the complex logistical challenge of airline crew scheduling by automating qualification verification, conflict checking, and duty roster assignments. The system:
- Maintains an up-to-date registry of aircraft, crew members, flight routes, and qualifications.
- Prevents invalid crew assignments through atomic, transactional business rules in Java.
- Enforces mandatory pre-flight and post-flight rest windows and limits consecutive duty hours.
- Protects concurrent edits across users through optimistic locking (`X-Expected-Version` headers).
- Delivers an intuitive, responsive Single Page Application (SPA) dashboard for flight dispatchers and crew coordinators.

> **Educational Simulation Notice:** Suggested duty-hour and rest limits in this project are configurable illustrative demonstration limits designed for academic simulation and do not constitute formal regulatory certification (e.g., FAA or DGCA Part 117/121).

---

## 2. Technology Stack

### Backend
- **Java 21 (LTS)** — Modern language features (records, pattern matching, virtual threads).
- **Spring Boot 4.1.2** — Web MVC, Data JPA, Validation, Starter Test.
- **PostgreSQL 17** — Relational database with strict referential integrity.
- **Flyway 10** — Versioned database schema migrations (`V1`, `V2`, `V3`).
- **Hibernate / JPA** — Object-relational mapping with optimistic locking (`@Version`).
- **OpenAPI 3.0 / Swagger** — Frozen API specification with interactive documentation at `/api-docs/index.html`.
- **JUnit 5 / MockMvc** — 203 automated backend tests (unit, integration, concurrency, security).

### Frontend
- **React 18** — Component-based Single Page Application.
- **TypeScript 5** — Strict type safety across domain models and API clients.
- **Vite 6** — Lightning-fast development server and production bundler.
- **TailwindCSS 3** — AeroRoster design system (deep navy, slate, restrained teal).
- **Lucide React** — Modern, accessible iconography.
- **Vitest & React Testing Library** — 181 automated frontend unit and integration tests.

---

## 3. Project Architecture

AeroRoster employs a clean, layered architectural model separating presentation, API contracts, domain logic, and data persistence:

```
┌─────────────────────────────────────────────────────────────────┐
│                   React 18 SPA (Frontend)                       │
│  Dashboard | Crew | Aircraft | Flights | Assign Crew | Leave    │
└───────────────────────────────┬─────────────────────────────────┘
                                │ JSON / REST (same-origin /api/v1)
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│               Spring Boot REST Controllers (Backend)            │
│  - Origin & Host Verification (anti-spoofing)                   │
│  - RFC-7807 Problem Detail Error Handling                       │
│  - Optimistic Concurrency Checks (X-Expected-Version)           │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│               Java Service Layer (Business Logic)               │
│  - Atomic Transactions (@Transactional READ_COMMITTED)          │
│  - Lock Hierarchy: Crew -> Flight -> Aircraft                   │
│  - SchedulingPolicy & DutyRules constraint validators           │
│  - Overlap prevention & mandatory rest window calculations      │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│             PostgreSQL 17 + Flyway Migrations (Database)        │
│  - V1__initial_schema.sql: Core schema & foreign key integrity   │
│  - V2__demo_seed.sql: Realistic seed data (airports, crew, etc) │
│  - V3__resource_versions.sql: Concurrency version columns       │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. Prerequisites

Before building and running the project, verify that the following tools are installed:

| Tool | Minimum Version | Verified Version | Notes |
|------|-----------------|------------------|-------|
| **Java JDK** | 21 | 21.0.x (Eclipse Temurin / OpenJDK) | Required for Spring Boot |
| **Apache Maven** | 3.9+ | 3.10.1 | Build tool |
| **Node.js** | 20+ | 20.x or 22.x LTS | Required for frontend build |
| **npm** | 10+ | 10.x | Package manager |
| **PostgreSQL** | 16+ | 17.x | Database server |

---

## 5. PostgreSQL Database Setup

AeroRoster requires two PostgreSQL databases:
1. `aeroroster_dev` — Used for running the application locally.
2. `aeroroster_test` — Used exclusively by automated integration tests.

Open `psql` or pgAdmin as the `postgres` superuser and execute:

```sql
-- 1. Create development database
CREATE DATABASE aeroroster_dev;

-- 2. Create test database
CREATE DATABASE aeroroster_test;
```

Flyway automatically applies all migrations (`V1`, `V2`, `V3`) upon application startup. No manual table creation is necessary.

---

## 6. Environment Variables

Configure connection settings using environment variables (or rely on the documented defaults):

| Variable | Default Value | Description |
|----------|---------------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/aeroroster_dev` | JDBC connection string for development database |
| `DB_USER` | `postgres` | Database username |
| `DB_PASSWORD` | *(empty)* | Database password |
| `PORT` | `8080` | Server HTTP port |
| `SERVER_ADDRESS` | `127.0.0.1` | Server bind IP address |
| `TEST_DB_URL` | `jdbc:postgresql://localhost:5432/aeroroster_test` | Dedicated JDBC URL for integration tests |
| `TEST_DB_USER` | `postgres` | Test database username |
| `TEST_DB_PASSWORD` | *(empty)* | Test database password |
| `VITE_API_BASE_URL` | `/api/v1` | Relative API path for frontend requests |

On Windows PowerShell:
```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/aeroroster_dev"
$env:DB_USER = "postgres"
$env:DB_PASSWORD = "YOUR_PASSWORD"
```

On Linux / macOS:
```bash
export DB_URL="jdbc:postgresql://localhost:5432/aeroroster_dev"
export DB_USER="postgres"
export DB_PASSWORD="YOUR_PASSWORD"
```

---

## 7. Backend Startup Instructions

### Standard Production / Standalone Build
The Maven build automatically triggers `npm ci` and the Vite production build inside `frontend/`, packaging the compiled static assets into `target/classes/static`.

1. **Package the complete full-stack application:**
   ```powershell
   mvn clean package -DskipTests
   ```
2. **Run the packaged JAR:**
   ```powershell
   java -jar target/aeroroster-0.1.0-SNAPSHOT.jar
   ```
3. **Open the application in your browser:**
   - Web Application: http://localhost:8080
   - Interactive API Docs: http://localhost:8080/api-docs/index.html
   - OpenAPI Contract JSON: http://localhost:8080/api/v1/openapi.json

Flyway will automatically validate and apply migrations on startup.

---

## 8. Frontend Startup Instructions

### Standalone Development Server (Fast HMR)
During active UI development, the Vite dev server can be run independently:

1. **Navigate to the frontend directory:**
   ```powershell
   cd frontend
   ```
2. **Install dependencies:**
   ```powershell
   npm install
   ```
3. **Start the Vite development server:**
   ```powershell
   npm run dev
   ```
4. **Access the dev server:**
   - The UI runs at `http://localhost:5173`.
   - Ensure the Spring Boot backend is running on `http://127.0.0.1:8080` to handle `/api/v1` calls.

---

## 9. Database Migrations

Database schema versioning is managed via Flyway migrations located in `src/main/resources/db/migration/`:

- **`V1__initial_schema.sql`** — Defines the core relational schema:
  - `airport` — IATA code, name, city, country, timezone.
  - `aircraft` — Registration, model, type rating, seating capacity, active status.
  - `crew` — Name, licence number, flight hours, contact info, active status.
  - `qualification` — Maps crew members to aircraft type ratings with issue/expiry dates.
  - `flight` — Flight number, departure/arrival airports, scheduled UTC departure/arrival timestamps, aircraft assignment, operational status.
  - `duty_assignment` — Links crew members to flights with assigned role (Captain, First Officer, Cabin Crew) and duty status.
  - `unavailability` — Tracks approved leave, medical leave, and mandatory training.
  - `flight_audit` — Immutable historical audit log of schedule modifications.
- **`V2__demo_seed.sql`** — Populates realistic seed records: 5 major airports, 3 aircraft, 12 certified crew members, qualification records, and scheduled demo flights.
- **`V3__resource_versions.sql`** — Adds optimistic locking `version` columns to support concurrency checks.

---

## 10. Key Features and Workflows

1. **Operational Dashboard (`/`)**
   - Live operational KPIs (active flights, available crew, aircraft utilization, pending assignments).
   - Real-time flight schedule timeline and duty status monitors.
   - Staffing shortage indicators with quick-action links.

2. **Flight Management (`/flights`)**
   - Comprehensive flight directory with filtering by route, date, and status.
   - Create new flights with UTC-normalized timestamp validation.
   - Visible action shortcuts: Edit flight, Cancel flight, and **Assign crew**.

3. **Interactive Flight Crew Assignment (`/flights/:flightId/assign`)**
   - Guided multi-step crew assignment workflow.
   - Aircraft type-rating qualification checks.
   - Rest period verification (enforces mandatory rest periods between consecutive duty shifts).
   - Conflict detection preventing double-booking of pilots or cabin crew.
   - Atomic multi-member assignment submission with optimistic version tokens.

4. **Crew Directory & Qualifications (`/crew`)**
   - Profile management with licence numbers, flight experience hours, and status.
   - Aircraft type qualification tracking with issue and expiry date monitoring.

5. **Aircraft Fleet Management (`/aircraft`)**
   - Fleet inventory tracking type ratings, seating capacity, and operational status.

6. **Crew Unavailability & Leave (`/unavailability`)**
   - Log medical leave, vacation, or simulator training periods to prevent duty scheduling during unavailable windows.

7. **Optimistic Concurrency & Security**
   - Stale-data protection via `X-Expected-Version: id:version` headers. Prevents overwriting changes when multiple dispatchers modify the same flight or assignment.
   - Hostname and origin validation protecting against unauthorized external cross-origin requests.

---

## 11. How to Run Tests

### Backend Test Suite (JUnit 5 + Spring Boot Test)
Requires the `aeroroster_test` database to exist.
```powershell
mvn test
```
- **Total Backend Tests:** 203 passing tests.
- **Coverage Areas:** Domain rules (`DutyRulesTest`, `SchedulingPolicyTest`), Persistence (`PersistenceIntegrationTest`), Service layer (`CoreServicesIntegrationTest`, `SchedulingIntegrationTest`), REST API (`ApiControllerTest`, `ApiIntegrationTest`), Concurrency (`AssignmentConcurrencyIntegrationTest`, `OptimisticConcurrencyHttpTest`), Security (`LocalApiSecurityRegressionTest`), and Workflow (`FlightCrewAssignmentWorkflowTest`).

### Frontend Test Suite (Vitest + React Testing Library)
```powershell
cd frontend
npm test
```
- **Total Frontend Tests:** 181 passing tests.
- **Coverage Areas:** Components, hooks, state management, form validation, modal dialogues, page routing, and guided flight crew assignment workflow.

### Frontend Lint and Type Check
```powershell
cd frontend
npm run lint
npx tsc --noEmit
```

### End-to-End Verification Scripts
Located in `scripts/`:
```powershell
# Verify interactive flight crew assignment workflow
python scripts/verify_flight_crew_assignment_e2e.py

# Verify REST API contracts and version tokens
python scripts/verify_phase25_package.py

# Verify live workflows
python scripts/verify_phase311_workflows.py
```
*(Ensure `aeroroster_test` is running and test credentials are set in environment variables before executing verification scripts).*

---

## 12. University Submission Package Structure

```
AeroRoster/
├── pom.xml                               # Maven project configuration
├── README.md                             # Comprehensive project documentation
├── AGENTS.md                             # Architecture rules and decision record
├── .gitignore                            # Version control exclusion rules
├── docs/                                 # Complete architecture & design specifications
│   ├── 01_SCOPE.md to 25_*.md            # Detailed milestone specifications
│   └── API_V1_BASELINE.sha256            # Frozen OpenAPI 1.1.0 checksum
├── src/
│   ├── main/
│   │   ├── java/com/aeroroster/          # Backend controllers, services, entities, DTOs
│   │   └── resources/
│   │       ├── application.properties    # Spring Boot configuration
│   │       ├── db/migration/             # Flyway SQL migrations (V1, V2, V3)
│   │       └── static/                   # OpenAPI specs and Swagger UI assets
│   └── test/
│       └── java/com/aeroroster/          # 203 backend automated tests
├── frontend/
│   ├── package.json                      # Frontend dependencies and scripts
│   ├── vite.config.ts                    # Vite build configuration
│   ├── tailwind.config.ts                # AeroRoster design system styling
│   ├── tsconfig.json                     # TypeScript compiler configuration
│   └── src/                              # React application components, pages, hooks
└── scripts/                              # Verification test scripts
```
