# Phase 3.1 — Frontend foundation

## Scope and architecture

Phase 3.1 adds a separate React workspace in `frontend/`; no backend source, migrations or OpenAPI contract changed. It is a Vite + React 18 + TypeScript application using Tailwind CSS, React Router, Axios and Lucide React. `src/components` holds focused shared controls, tables, status badges and shell/navigation; `src/pages` holds route-specific information layouts; `src/api` holds the future integration client; and `src/types` holds shared API shapes.

All current page content is explicitly labelled sample data for visual development. It is not fetched, operational, or authoritative.

## Setup

```powershell
cd frontend
npm install
Copy-Item .env.example .env
npm run dev
```

`VITE_API_BASE_URL` defaults in the example to `http://127.0.0.1:8080/api/v1`. Axios is configured but no endpoint calls are active in this phase. The client includes the documented `ApiError`, `PageResponse`, and `expectedVersion(id, version)` helper so a future integration can use original edit snapshots correctly.

## Security and API integration note

The frozen local API rejects cross-origin browser requests and binds to loopback. Do not add wildcard CORS or a Vite proxy that conceals an origin change. For a later live UI integration, serve the built files from the backend origin or agree a reverse-proxy approach with the backend owner. Conditional mutations are only the thirteen documented OpenAPI operations; retain the original `X-Expected-Version: id:version`, and on `409 STALE_VERSION` reload/review rather than silently replacing the token.

## Routes

`/dashboard`, `/crew`, `/aircraft`, `/flights`, `/qualifications`, `/assignments`, and `/unavailability` are available. Unknown paths redirect to `/dashboard`.

## Design system

Tokens are reflected in Tailwind and CSS: primary navy `#10243A`, secondary navy `#183B56`, teal `#16A6A0`, canvas `#F3F6FA`, card white, text `#172B3A`/`#64748B`, and border `#E2E8F0`. Semantic statuses use green, amber, red and blue. Tables and dense, role-specific operational views take precedence over decorative cards. Controls have keyboard focus indicators and the sidebar becomes an accessible overlay navigation at small widths.

## Verification

```powershell
npm run lint
npm run build
```

No automated frontend test runner is introduced in this foundation. Manual route/navigation and responsive checks should be performed from `npm run dev`. A next phase can introduce contract-backed read integration, loading/error states, then reviewed CRUD forms while preserving OpenAPI version-precondition rules.

## Current limitations

No authentication, real API reads/writes, CRUD, scheduling logic, calendar, reporting or allocation capability is implemented. Browser-facing mutations are deliberately absent. The Java backend and its local-only security protections remain unchanged.
