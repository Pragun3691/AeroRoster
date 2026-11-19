# Phase 3.4 — Aircraft management UI

## Architecture

`AircraftManagementPage` owns `/aircraft` and `/aircraft/:aircraftId`; `aircraftData.ts` holds typed, explicitly demo-only aircraft and type-reference records. The module reuses shared page headers, buttons, tables, empty states, and status badges. It provides client-side search, type/status filters, sort, profile navigation, validated local forms, a confirmation-state activity review, and a reusable aircraft-type reference surface.

## Contract boundary

The frozen `AircraftRequest` contains only `registration` and `aircraftTypeId`; forms expose exactly those create/update fields. `active` is clearly identified as the separate future activity operation. Aircraft type display follows `AircraftTypeView` fields: model, manufacturer, capacity and minimum cabin crew. No API call or eligibility/availability calculation is made. Future PUT/PATCH calls must retain the original AircraftView `id:version` as `X-Expected-Version`.

## Demo data and responsiveness

Registration, type and associated flights are sample data. Flight associations do not claim operational availability. Directory filters collapse to a vertical layout while its information-dense table remains in the shared horizontal scroll wrapper; profiles and type-reference cards stack on narrow screens.

## Tests and limitations

Tests cover directory rendering, search/filter/no-results behavior, forms, preserved edit values, and profile association display. Run `npm run lint`, `npm run test`, and `npm run build` in `frontend/`. This phase does not fetch/create/update aircraft, modify the backend, infer requirements, make flight changes, or implement scheduling.
