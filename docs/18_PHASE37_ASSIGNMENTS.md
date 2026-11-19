# Phase 3.7 — Assignment Management UI

## Scope

Adds demo-only `/assignments` and `/assignments/:assignmentId` management views. The module uses typed local AssignmentView- and FlightCrewCoverage-shaped data. No backend calls, scheduling calculation, or backend file change is made.

## Contract inspected

- `POST /assignments/eligibility` and `POST /assignments` accept `AssignmentRequest(crewId, flightId, assignedRole)`; creation has no expected-version header.
- `GET /assignments/{assignmentId}` returns `AssignmentView(id, crewId, flightId, flightNumber, assignedRole, dutyStartTime, dutyEndTime, status, createdAt, version)`.
- `PUT /assignments/{assignmentId}/cancellation` retains history and requires the assignment’s original `X-Expected-Version: id:version`.
- `POST /assignments/{assignmentId}/reassignments` is permitted only after cancellation, reuses the prior crew/flight/role, and requires that cancelled assignment’s original version token. It does not accept a replacement crew.
- No global `GET /assignments` exists. Future lists compose crew/flight scoped history endpoints. `GET /flights/{flightId}/coverage` supplies real coverage and reason data.

## Demo and limitations

Coverage and eligibility are captured demo-response shapes, never frontend calculations. The review form only validates required AssignmentRequest fields and labels its eligible response as local sample data. Local creation/cancellation mutates only component state. Cancellation is confirmed and preserves visible history. The demo does not implement reassignment because the real operation creates a new assignment only from an already-cancelled record and must recheck backend eligibility.

## Verification

Run `npm run lint`, `npm run test`, and `npm run build` from `frontend/`. Tests cover metrics, filtering/sorting, detail/not-found, local assignment validation/creation, and cancellation confirmation. Browser visual verification is reported in the completion report.
