# Phase 3.8 — Unavailability Management UI

Adds demo-only `/unavailability` and `/unavailability/:unavailabilityId` routes with UTC register/detail views, metrics, search/filter/sort, local add/edit/remove confirmations, and captured boolean conflict markers.

## Contract

`UnavailabilityRequest` contains required `startTime`, `endTime`, and trimmed nonblank `reason` (1–200 characters). The backend validates start before end, prevents overlapping unavailability and active-duty conflicts. `UnavailabilityView` returns id, crewId, start/end instants, reason, and version. `GET /crew/{crewId}/unavailability/conflicts?start=&end=` returns only `UnavailabilityConflictView { conflicts }`.

POST/GET `/crew/{crewId}/unavailability` create/list; GET/PUT/DELETE `/crew/{crewId}/unavailability/{periodId}` read/update/remove. Creation has no version header; PUT and DELETE require original `X-Expected-Version: periodId:version`. Future integrations must show 409 conflict/stale responses and 428 missing-precondition responses without silently refreshing tokens.

## Limits

All data/actions are local demo behavior and never call the API. Conflict flags are captured sample response values, not frontend calculations. UTC labelled datetime-local fields are serialized to demo `Z` timestamps; a future integration must submit valid offset/Z timestamps. Backend conflict/duty logic remains server-owned.

## Verification

Run `npm run lint`, `npm run test`, and `npm run build` in `frontend/`. Tests cover metrics, UTC/conflict display, search/filter/no result, detail/not-found, validation, local add confirmation, and remove confirmation. Browser verification status is recorded in the completion report.
