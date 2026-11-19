# Phase 3.3 — Crew management UI

## Architecture

The `/crew` and `/crew/:crewId` routes use `CrewManagementPage`. Typed interface-only records are isolated in `src/pages/crew/crewData.ts`. The route contains three local presentation states: a searchable/sortable directory, validated add/edit form, and detailed profile. It reuses the Phase 3.1 application shell, controls, tables, empty state, page header, and status badge rather than altering the global design system.

## Data and future API boundary

All personnel, qualification, and assignment information is explicitly labelled sample data. No Axios calls are active. The future `CrewRequest` boundary is reflected by the local name, role, licence number, experience years, contact, and active fields, but no request DTO or endpoint is invented. A later PUT/PATCH integration must preserve the original `CrewView` `id:version` as `X-Expected-Version`; it must not refresh that client value silently after a stale response.

## Interactions and validation

Search considers name and licence. Role, active-state, and sort controls are functional client-side demo controls. Add/edit forms require name, licence, contact, and a nonnegative whole-number experience value. Edit starts from existing values. Profile status review uses a confirmation state and reports that no server update occurred. Qualification states distinguish valid, expiry review, expired, and missing administrative information without inferring any requirements.

## Responsive behavior

The directory controls collapse from four columns to a vertical layout, while the dense directory table stays inside the shared horizontal-scroll wrapper on narrow displays. Profile records use responsive two-column details and stack at smaller widths. The existing mobile navigation overlay is unchanged.

## Tests and verification

`CrewManagementPage.test.tsx` covers directory rendering, search, role/active filtering, add/edit validation, existing edit values, profile qualification/empty assignment state, and no-results state. Run `npm run lint`, `npm run test`, and `npm run build` in `frontend/`.

## Limitations and next phase

This phase does not create, update, toggle, or fetch crew records; it has no authentication or live qualification/assignment API integration. A later reviewed phase can introduce same-origin read integration, then conditional mutations governed by the frozen API contract.
