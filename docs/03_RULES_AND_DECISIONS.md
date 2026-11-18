# Business rules and decisions (Phase 1 draft)

## Confirmed approach

- **Separate project** from the old Oracle archive.
- Java 21 / Spring Boot 4.1.2 / PostgreSQL; web UI uses Thymeleaf.
- Flight is a dated instance: unique flight number + departure instant, not number alone.
- AircraftType models are separate from registered aircraft.
- All timestamps stored as PostgreSQL `timestamptz`, user-visible display may use local time zones.

## Illustrative scheduling policy — configurable before Phase 2

- Duty begins 60 minutes before scheduled departure; ends 30 minutes after scheduled arrival.
- No two active duty windows for the same crew may overlap; no two active flight windows for the same aircraft may overlap.
- Minimum rest between duty windows: 10 hours (demo setting).
- Maximum duty hours charged to any UTC calendar date: 14 hours (demo setting); split cross-midnight intervals across UTC dates.
- Must be active, assigned role must match crew role, qualification must match aircraft type + role and be valid on flight departure date, and requested window must not intersect crew unavailability.
- At least one Pilot and one Co-Pilot must be present to mark a demo flight staffed. Cabin crew minimum is the illustrative setting in AircraftType.
- A scheduled flight with active assignments cannot have its times or aircraft changed until assignments are removed/revalidated. Cancelled flights cannot receive active assignments.
- Add/edit/unassign/assign must be atomic. For concurrent assignment requests, acquire crew-level and aircraft-level locks in a stable order before rechecking and committing; do not assume read-then-write alone is race-safe.
- Same candidate cannot hold two active assignments for the same flight (`UNIQUE ... WHERE status='ACTIVE'`).

These are not regulatory limits and do not imply certification under DGCA or other aviation law.

## Unresolved course requirement

If the Java teacher requires *Core Java / Swing / JavaFX* instead of Spring Boot, keep the data model and domain rules but replace the UI/backend architecture accordingly. Get confirmation before Phase 2.

## Phase 2.1 persistence decisions

The project owner explicitly authorized the existing Spring Boot architecture for Phase 2.1 on 18 November 2026. This milestone implements the existing schema without schema changes. Persistence decisions, relationships, repository responsibilities and verification evidence are recorded in `06_PHASE21_PERSISTENCE.md`.

Assignment validation and stable-order locking are implemented in Phase 2.3. Repository writes alone provide structural persistence and must be composed within service transactions.

## Phase 2.2 service decisions

The project owner authorized Phase 2.2 on 18 November 2026. Core services use record DTOs, Jakarta validation, typed business errors, an injected UTC Clock and Spring transactional boundaries. Aircraft row locks serialize aircraft occupancy checks. Flight edits lock the flight before both affected aircraft in ascending ID order, and crew/qualification edits share a crew lock. Locked records are refreshed before validation.

Crew deactivation rejects ongoing/future active duties. Aircraft deactivation rejects ongoing/future scheduled flights. Crew role changes, aircraft type changes and qualification mutations protect referenced history. Active assignments prevent material flight edits and cancellation. Completed/cancelled flights are terminal. All detailed policies and the Phase 2.3 locking handoff are in `07_PHASE22_SERVICES.md`.

## Phase 2.3 scheduling decisions

The owner authorized Phase 2.3 after the Phase 2.2 checkpoint commit. SchedulingPolicy retains the existing duty buffers, ten-hour rest, 14-hour UTC-day cap and inclusive qualification expiry on the UTC departure date. It adds the requested defaults for previously undefined rolling windows, ten hours per rolling 24 hours and 40 hours per rolling seven days. All values are configurable academic settings.

Assignment operations use crew → flight → aircraft row locks, then evaluate current eligibility and persist a duty snapshot. Cancellation preserves history. Scalar projections and refreshed owned child records prevent stale cached safety decisions. Write transactions explicitly require writable READ_COMMITTED isolation. Rules, frontend contracts, calculation details, concurrent transaction evidence and limitations are documented in `08_PHASE23_SCHEDULING.md`.

## Phase 2.4 REST decisions

The owner authorized the REST layer after the Phase 2.3 checkpoint commit. Thin /api/v1 controllers reuse existing service DTOs and business operations. Growing collections use stable PageResponse records and database-backed pagination. Strict JSON binding rejects unknown managed fields, coercion and invalid timestamp shapes. Typed errors map through a shared API error contract, with eligibility details retained on assignment rejection.

The unauthenticated API binds to loopback by default and enforces a same-origin browser policy. OpenAPI and a local documentation viewer are provided without new runtime dependencies. Contracts, security limitations, frontend examples and verification evidence are documented in `09_PHASE24_REST_API.md`.
