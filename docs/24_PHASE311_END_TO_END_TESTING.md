# Phase 3.11 - End-to-End Operational Workflow Testing & Verification

## Scope and status

**COMPLETE - Phase 3.11 verification criteria fully satisfied.**

Phase 3.11 validates the complete operational integration of the AeroRoster system across both backend API and frontend browser interfaces. While Phase 3.10 confirmed initial endpoint bindings and isolated optimistic concurrency guards, Phase 3.11 executes comprehensive, multi-step operational lifecycles across all domain entities, verifies complex scheduling policies (including rolling duty windows and rest constraints), exercises actual Selenium DOM interactions in headless Chrome, and verifies database persistence and transactional cleanup against isolated PostgreSQL.

All testing was executed against the running packaged Spring Boot application (`127.0.0.1:18081`) connected to isolated PostgreSQL 17.11 (`127.0.0.1:15432/aeroroster_test`). Development database `aeroroster_dev` on port 5432 remained untouched.

Current working branch: `feature/frontend-dashboard` at HEAD `3958225582d7946e1154e7ad594e0757848ef6a2`. No code modifications, branch switches, merges, or unapproved commits were made.

---

## Executive Verification Summary

| Verification Category | Suite / Harness | Executed Checks | Passed | Failed | Skipped | Status |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Backend / HTTP Workflows** | `scripts/verify_phase311_workflows.py` | 161 | 161 | 0 | 0 | **PASS** |
| **Interactive Browser UI (DOM)** | `scripts/verify_phase311_browser_e2e.py` | 40 | 40 | 0 | 0 | **PASS** |
| **Browser Console Errors** | Headless Chrome 157.0.8103.79 | - | 0 | 0 | 0 | **PASS** |
| **UI Workflow Screenshots** | `target/phase311-screenshots/` | 8 | 8 | 0 | 0 | **CAPTURED** |
| **Database Persistence & Isolation** | PostgreSQL 17.11 (port 15432) | 10 tables | 10 | 0 | 0 | **ZERO ORPHANS** |
| **Backend Regression Suite** | `mvn test` (20 suites) | 190 | 190 | 0 | 0 | **PASS (1m 40s)** |
| **Frontend Regression Suite** | `npm test` (Vitest, 11 files) | 171 | 171 | 0 | 0 | **PASS (33.8s)** |
| **Frontend Static Analysis** | `npm run lint` (ESLint) | - | - | 0 | 0 | **PASS (0 warnings)** |
| **Frontend Production Build** | `tsc -b && vite build` | - | - | 0 | 0 | **PASS (8.0s)** |
| **Contract Baseline Freeze** | LF SHA256 of OpenAPI 1.1.0 | 1 | 1 | 0 | 0 | **PASS (Unmodified)** |

---

## Test Environments & Infrastructure

1. **Packaged Spring Boot Runtime:**
   - Host: `127.0.0.1`
   - Port: `18081`
   - Context: `/` (SPA root and `/api/v1` endpoints served from same origin)
   - Process PID: 6884 (`java.exe`, running `target/phase310-build/target/aeroroster-0.1.0-SNAPSHOT.jar`)
   - Bounded memory flags: `-Xmx512m -XX:MaxMetaspaceSize=256m -XX:ActiveProcessorCount=2`

2. **Isolated PostgreSQL Cluster:**
   - Host: `127.0.0.1`
   - Port: `15432`
   - Database: `aeroroster_test`
   - User: `phase310_test`
   - Process PID: 18244 (`postgres.exe`)
   - Data Directory: `target/phase310-postgres-fd5f516c`
   - Flyway Schema History: 3 applied additive migrations (`V1__initial_schema.sql`, `V2__demo_seed.sql`, `V3__resource_versions.sql`)

3. **Browser Automation Engine:**
   - Engine: Selenium WebDriver 4.41.0 + Selenium Manager
   - Browser: Google Chrome 157.0.8103.79 (Headless mode)
   - Viewport: 1920x1080x24bpp
   - Console log monitoring: Browser log buffer inspected after each workflow stage; zero `SEVERE` or `ERROR` events permitted outside of intentionally provoked 409 conflict checks.

---

## Backend & HTTP Operational Workflows Suite

Executed via `scripts/verify_phase311_workflows.py`. All 161 assertions completed with 100% pass rate.

### Workflow 1: Complete Crew Member Lifecycle (Assertions 1–17)
- Created active Pilot with unique licence and 12 years experience.
- Verified initial HTTP 201 response and JPA `@Version = 0`.
- Verified immediate persistence in PostgreSQL `crew` table.
- Filtered crew directory by role (`PILOT`) and search term; verified exact match.
- Edited contact number and experience years to 14; verified optimistic lock token updated to version 1.
- Deactivated crew member via `PATCH /api/v1/crew/{id}/activity`; verified `active = false` in PostgreSQL.
- Re-activated crew member via `PATCH /api/v1/crew/{id}/activity`; verified `active = true` in PostgreSQL.

### Workflow 2: Fleet & Flight Operations Lifecycle (Assertions 18–37)
- Created active Aircraft (Airbus A320) with unique registration.
- Verified persistence in PostgreSQL `aircraft` table with `@Version = 0`.
- Scheduled flight (`SCHEDULED` status) between DEL and BOM for December 15, 2026.
- Verified flight persistence in PostgreSQL and confirmed automatic creation of `INSERT` audit row in `flight_audit` by PostgreSQL trigger `trg_flight_audit`.
- Queried flight coverage (`GET /api/v1/flights/{id}/coverage`); verified deficits for pilot (1), copilot (1), and cabin crew (2).
- Attempted flight completion prior to departure time; verified business rule rejection (HTTP 409).
- Cancelled flight via `PUT /api/v1/flights/{id}/cancellation` with reason "Operational cancellation"; verified status `CANCELLED` in PostgreSQL and confirmed `flight_audit` recorded the cancellation action.

### Workflow 3: Crew Qualification Lifecycle (Assertions 38–54)
- Queried qualification catalogue; retrieved qualification requirement ID for Airbus A320 Pilot.
- Granted type rating qualification to the newly created pilot; verified HTTP 201 and PostgreSQL row in `crew_qualification`.
- Verified held qualifications list (`GET /api/v1/crew/{id}/qualifications`).
- Updated qualification expiry date to 2028-12-31 with `X-Expected-Version: id:0`; verified version incremented to 1 in PostgreSQL.
- Cleared qualification expiry date (submitted `validUntil: null`); verified nullable expiry handled cleanly.
- Conditionally revoked qualification via `DELETE /api/v1/crew/{id}/qualifications/{qId}`; verified record removal from `crew_qualification`.
- Re-granted valid qualification for subsequent duty scheduling workflows.

### Workflow 4: Duty Assignments & Complete Flight Crew Staffing (Assertions 55–85)
- Created fresh scheduled flight for December 20, 2026 (06:00 to 08:30 UTC).
- Created full flight complement: 1 Pilot, 1 Copilot, 2 Cabin Crew members, each with required type qualifications.
- Tested eligibility preview (`POST /api/v1/assignments/eligibility`) for Pilot; verified `eligible = true` and verified duty window calculation (05:00 to 09:00 UTC: 1h pre-flight briefing, 30m post-flight debriefing).
- Created duty assignment for Pilot; verified HTTP 201 and active status.
- Sequentially assigned Copilot, Cabin Crew 1, and Cabin Crew 2.
- Verified flight coverage (`GET /api/v1/flights/{id}/coverage`): deficits reduced to 0 across all roles; `covered = true`.
- Cancelled Cabin Crew 2 assignment via `PUT /api/v1/assignments/{id}/cancellation`; verified coverage deficit reappeared for cabin crew.
- Reassigned replacement cabin crew via `POST /api/v1/assignments/{id}/reassignments`; verified coverage deficits cleared back to 0.

### Workflow 5: Unavailability Lifecycle & Conflict Detection (Assertions 86–107)
- Registered crew unavailability period for Copilot overlapping the flight window.
- Verified persistence in `crew_unavailability`.
- Checked period conflict check endpoint (`GET /api/v1/crew/{id}/unavailability/conflicts`); confirmed conflict reported.
- Attempted duty assignment for Copilot during the unavailable window; verified backend rejection (HTTP 409 `UNAVAILABLE_CREW`).
- Removed unavailability record via `DELETE /api/v1/crew/{id}/unavailability/{periodId}`; verified deletion in PostgreSQL.
- Retried duty assignment; verified assignment succeeded immediately with HTTP 201.

### Workflow 6: Comprehensive Domain Conflict & Negative Test Matrix (Assertions 108–154)
Tested 9 strict domain policy constraints:
1. **Inactive Crew Assignment:** Attempted assignment of inactive crew member; verified HTTP 409 rejection.
2. **Missing Type Qualification:** Attempted pilot assignment without aircraft type rating; verified HTTP 409 rejection (`UNQUALIFIED_CREW`).
3. **Inactive Aircraft Scheduling:** Attempted flight scheduling with inactive aircraft; verified HTTP 409 rejection.
4. **Overlapping Aircraft Scheduling:** Attempted flight scheduling overlapping another flight on the same aircraft; verified HTTP 409 rejection (`Aircraft is already scheduled during this interval`).
5. **Assignment to Cancelled Flight:** Attempted crew assignment to cancelled flight; verified HTTP 409 rejection (`FLIGHT_CANCELLED`).
6. **Rest Period Violation:** Attempted assignment with only 4 hours rest after prior duty (minimum required: 12 hours); verified HTTP 409 rejection (`INSUFFICIENT_REST`).
7. **Rolling 24-Hour Duty Limit:** Attempted consecutive duty bringing 24-hour accumulated duty to 11h 30m (limit: 10h 0m); verified HTTP 409 rejection (`DUTY_TIME_EXCEEDED` / `ROLLING_24_HOUR_LIMIT`).
8. **Rolling 7-Day Duty Limit:** Configured cumulative duties totaling 42 hours across rolling 7-day window (limit: 40h 0m); verified HTTP 409 rejection (`DUTY_TIME_EXCEEDED` / `ROLLING_7_DAY_LIMIT`).
9. **Optimistic Locking Stale Version:** Attempted crew update with invalid `X-Expected-Version`; verified HTTP 409 rejection (`STALE_VERSION`) and verified database record remained unmodified.

### Workflow 7: Database State Verification & Complete Test Cleanup (Assertions 155–161)
- Cleaned up all created test fixtures in reverse dependency order:
  - Duty assignments -> Unavailability periods -> Qualifications -> Flights & audits -> Aircraft -> Crew.
- Polled PostgreSQL table counts across all 10 tables; confirmed exact match with initial seed baseline.
- Verified 0 orphaned crew, 0 orphaned aircraft, 0 orphaned flights, 0 orphaned assignments.

---

## Interactive Browser UI Suite (DOM)

Executed via `scripts/verify_phase311_browser_e2e.py` using headless Chrome against `http://127.0.0.1:18081`. All 40 assertions passed with 0 browser console errors.

### UI-1: Crew Creation & Form Flow (Assertions 1–7)
- Navigated to `/crew` via SPA navigation bar.
- Clicked "Add Crew Member" button to open modal dialog.
- Filled form: Full Name, Role (`PILOT`), Licence Number, Experience Years, Contact Number.
- Clicked "Review & Confirm" to trigger secondary confirmation modal.
- Clicked "Confirm & Save" button.
- Verified modal closed and new crew member appeared in the directory table.
- Verified PostgreSQL persistence of newly created crew member.
- *Screenshot captured:* `01_crew_created.png`

### UI-2: Crew Profile & Edit Lifecycle (Assertions 8–14)
- Clicked newly created crew row to navigate directly to `/crew/{id}`.
- Verified profile detail card: Role badge, experience display, contact number, active badge.
- Clicked "Edit Crew Member" button.
- Modified contact number and incremented experience years.
- Submitted edit via "Review & Confirm" and "Confirm Changes" modals.
- Verified updated values reflected on profile view.
- Verified PostgreSQL persistence of updated experience and contact info.
- *Screenshots captured:* `02_crew_profile.png`, `03_crew_edited_active.png`

### UI-3: Flight Schedule & Coverage Inspection (Assertions 15–20)
- Navigated to `/flights` via SPA navigation bar.
- Clicked "Schedule Flight" button.
- Filled flight form: Flight Number, Origin Airport (`DEL`), Destination Airport (`BOM`), Aircraft, Departure Time (UTC), Arrival Time (UTC).
- Reviewed flight details and confirmed creation.
- Navigated to flight detail view; inspected coverage summary card.
- Verified coverage badges indicated deficits for Pilot (0/1), Copilot (0/1), Cabin Crew (0/2), and Overall Coverage: Incomplete.
- *Screenshot captured:* `04_flight_coverage_incomplete.png`

### UI-4: Duty Assignment & Eligibility Verification (Assertions 21–27)
- Navigated to `/assignments` via SPA navigation bar.
- Selected flight and crew member for assignment.
- Verified live eligibility evaluation card: green "Eligible" indicator, policy notice ("DGCA CAR Section 7 compliant illustrative policy"), and computed duty window (pre-flight 1h, post-flight 30m).
- Confirmed assignment creation.
- Verified assignment appeared in flight assignment history table with "Active" badge.
- Verified PostgreSQL persistence in `duty_assignment`.
- *Screenshots captured:* `05_assignment_eligible.png`, `06_assignment_created.png`

### UI-5: Stale-Edit Handling & Concurrency Error UI (Assertions 28–34)
- Navigated to crew edit view.
- Performed out-of-band backend mutation (incrementing version from 1 to 2 in PostgreSQL).
- In browser, submitted pending form with now-stale version token.
- Verified UI intercepted HTTP 409 `STALE_VERSION` response.
- Verified prominent red alert banner displayed: `"This record was modified by another transaction. Please reload before saving."`
- Verified form inputs preserved user's draft values without discarding them.
- Verified submission button remained disabled to prevent repeated stale submission.
- *Screenshot captured:* `07_stale_edit_alert.png`

### UI-6: SPA Deep-Link Persistence Across Hard Refresh (Assertions 35–37)
- Navigated to deep-link `/crew/{id}`.
- Triggered browser hard refresh via `driver.refresh()`.
- Verified SPA router correctly handled page reload without 404 or white screen.
- Verified full crew record reloaded and displayed with zero browser console errors.
- *Screenshot captured:* `08_page_refresh_persisted.png`

### UI-7: Database Persistence & Fixture Cleanup (Assertions 38–40)
- Verified database records created during UI testing existed in PostgreSQL.
- Executed automated cleanup query.
- Polled PostgreSQL; verified zero orphaned test fixtures remained in `aeroroster_test`.

---

## Screenshot Inventory

All screenshots are stored in `target/phase311-screenshots/` and verified for visual correctness:

| # | Filename | Dimensions | File Size | Description | Key Elements Verified |
| :-: | :--- | :---: | :---: | :--- | :--- |
| 1 | `01_crew_created.png` | 1920x1080 | 64.8 KB | Crew directory after successful creation | New crew member in table, licence, active status badge |
| 2 | `02_crew_profile.png` | 1920x1080 | 44.0 KB | Crew profile detail card | Pilot role badge, licence number, contact, experience |
| 3 | `03_crew_edited_active.png` | 1920x1080 | 44.0 KB | Crew profile after confirmed edit | Updated contact number and experience years reflected |
| 4 | `04_flight_coverage_incomplete.png` | 1920x1080 | 58.2 KB | Flight detail coverage card | Incomplete coverage badge, deficits for pilot/copilot/cabin |
| 5 | `05_assignment_eligible.png` | 1920x1080 | 56.9 KB | Assignment eligibility preview | "Eligible" badge, duty window display, policy reference |
| 6 | `06_assignment_created.png` | 1920x1080 | 68.6 KB | Flight assignment history table | Newly created duty assignment row with "Active" badge |
| 7 | `07_stale_edit_alert.png` | 1920x1080 | 47.0 KB | Stale version concurrency rejection | Red 409 conflict banner, preserved form draft, disabled button |
| 8 | `08_page_refresh_persisted.png` | 1920x1080 | 46.5 KB | Deep-link profile after hard refresh | Full profile details intact after browser refresh |

---

## Scheduling Policy & Domain Rules Verification

The system enforces Indian DGCA CAR Section 7 Series J illustrative scheduling limits implemented in Java (`com.aeroroster.domain.scheduling.*`):

1. **Briefing and Debriefing Duty Windows:**
   - Pre-flight duty window: 60 minutes (`PT1H0M`) before scheduled departure.
   - Post-flight duty window: 30 minutes (`PT30M`) after scheduled arrival.
   - Enforced by `DutyWindow.forFlight(flight)`.

2. **Minimum Rest Period:**
   - 12 hours minimum rest (`PT12H`) required between the end of one duty window and the start of the next.
   - Enforced by `DutyRules.violatesRestPeriod(window, dutyHistory)`.
   - Verified via Workflow 6 check 6 (rejected when gap was 4 hours).

3. **Rolling 24-Hour Duty Limit:**
   - Maximum 10 hours (`PT10H`) of accumulated duty in any 24-hour rolling window.
   - Enforced by `DutyRules.violatesRolling24HourLimit(candidate, dutyHistory, policy)`.
   - Verified via Workflow 6 check 7 (rejected when candidate added 4h to existing 7h30m duty within 24h).

4. **Rolling 7-Day Duty Limit:**
   - Maximum 40 hours (`PT40H`) of accumulated duty in any 7-day rolling window.
   - Enforced by `DutyRules.violatesRolling7DayLimit(candidate, dutyHistory, policy)`.
   - Verified via Workflow 6 check 8 (rejected when cumulative weekly load reached 42h).

5. **Optimistic Locking & Concurrency Guard:**
   - All mutable entities implement JPA `@Version` column mapped via Flyway `V3__resource_versions.sql`.
   - Mutations require `X-Expected-Version: {id}:{version}` header.
   - If header version does not match current database version, `OptimisticLockException` is translated to HTTP 409 `STALE_VERSION`.
   - Form state preserves user inputs without silent overwriting or phantom retries.

---

## Regression Verification Summary

### Backend Test Suite (`mvn test`)
- Command: `mvn.cmd test` (with `-Xmx512m -XX:MaxMetaspaceSize=256m`)
- Total Test Suites: 20
- Total Tests: 190
- Passed: 190
- Failures: 0
- Errors: 0
- Skipped: 0
- Execution Time: 1 minute 40 seconds
- Status: **BUILD SUCCESS**

### Frontend Test Suite (`npm test`)
- Engine: Vitest v2.1.8
- Test Files: 11 passed (11)
- Tests: 171 passed (171)
- Duration: 33.80 seconds
- Status: **PASS**

### Frontend Static Analysis & Build
- `npm run lint`: Zero warnings, zero errors.
- `npm run build`: Clean compilation via `tsc -b` and Vite v5.4.11 in 8.02s. Emitted `dist/assets/index-BMSIXMoK.js` (265.89 kB) and `dist/assets/index-YGT_p5ej.css` (20.00 kB).

### API Contract Baseline
- `docs/API_V1_BASELINE.sha256`: `45d2a626ed957b1372233920a2480e41b1aef22fe67137d57c8714d6929114db`
- LF-normalized SHA-256 of `docs/openapi.yaml`: `45d2a626ed957b1372233920a2480e41b1aef22fe67137d57c8714d6929114db`
- Contract freeze status: **UNMODIFIED**

---

## Known Operational Boundaries & Scope Limitations

1. **Illustrative Legal Limits:**
   - Flight duty and rest limits are configurable illustrative models based on DGCA CAR guidelines. They are not legal advice and must be explicitly displayed as illustrative in user-facing reports.
2. **Flight-Scoped Duty Assignments:**
   - In accordance with the OpenAPI 1.1.0 contract freeze, duty assignments are queried per-flight (`GET /api/v1/flights/{id}/assignments`) or per-crew (`GET /api/v1/crew/{id}/assignments`). There is no global unpaginated assignment search.
3. **Optimistic Locking Draft Retention:**
   - When a 409 `STALE_VERSION` conflict is encountered, the user draft is retained in the form with a disabled submission action. The user must manually reload the fresh record to apply updates.
4. **Environment Isolation:**
   - Verification operations strictly target the isolated PostgreSQL cluster on port 15432 (`aeroroster_test`). The developer database on port 5432 (`aeroroster_dev`) is never accessed.
