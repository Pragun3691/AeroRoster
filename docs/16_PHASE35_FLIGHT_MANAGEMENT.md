# Phase 3.5 — Flight Management UI

## Scope

Phase 3.5 adds a local, demo-only Flight Management UI to the existing React frontend. It does not call the backend or alter backend Java, Flyway migrations, security configuration, or the frozen OpenAPI contract.

## Pages and routes

- `/flights` — searchable, filterable, sortable demo flight directory.
- `/flights/:flightId` — read-only demo flight detail view.

The directory supports flight-number/origin/destination search, status filtering, a departure-date filter, departure or flight-number sorting, and reset/no-result states. Tables use the existing horizontally scrollable responsive table container.

## Components and data

- `frontend/src/pages/flights/flightData.ts` provides strict TypeScript demo data and local airport/aircraft references.
- `frontend/src/pages/flights/FlightManagementPage.tsx` contains the directory, detail view, local add/edit form, validation, and status display. It reuses the existing shared components rather than adding generic dashboard cards.
- `frontend/src/pages/flights/FlightManagementPage.test.tsx` covers rendering, filters, sorting, empty state, detail route, and local form behaviour.

## Contract alignment

The data shape follows frozen OpenAPI 1.1.0 `FlightView`: `id`, flight number, source/destination airport IDs and IATA codes, aircraft IDs/registration/type ID, departure and arrival timestamps, status, and version.

Add/edit fields are limited to actual `FlightRequest` properties: `flightNumber`, `sourceAirportId`, `destinationAirportId`, `aircraftId`, `departureTime`, and `arrivalTime`. Status is not offered as a create/edit field because it is not in `FlightRequest`. Backend request timestamps require ISO-8601 with an offset or `Z`; the local date-time controls need serialization in a later API phase.

Future conditional flight updates must preserve the original version and send `X-Expected-Version: id:version` only where the API contract requires it. This phase makes no HTTP requests.

## Demo-only limitations

- Records, references, aircraft associations, and statuses are clearly labelled local demo data; they do not establish availability, crew coverage, assignment state, or a persisted schedule.
- Form success means local required-field validation only. It never changes data or calls an endpoint.
- No crew allocation, assignment management, scheduling logic, status mutation, authentication, or API integration is implemented.
- Backend validation rules, including temporal ordering, remain backend responsibilities.

## Verification

From `frontend/` run `npm run lint`, `npm run test`, and `npm run build`. Route smoke checks and any browser-verification limitation are recorded in the Phase 3.5 completion report.

## Next phase

Owner review should precede a later approved API-integration phase.
