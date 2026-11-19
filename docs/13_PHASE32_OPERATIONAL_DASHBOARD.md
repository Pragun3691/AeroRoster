# Phase 3.2 — Operational dashboard

## Scope

Phase 3.2 enhances only the `/dashboard` route inside `frontend/`. It remains a visual-development dashboard: it makes no API request, offers no write workflow, and does not alter backend Java, Flyway migrations, or OpenAPI 1.1.0.

## Architecture and demo data

`src/pages/dashboard/dashboardData.ts` is the single typed sample-data source for operational metrics, flights, crew distribution, alerts, activity, and chart values. Route composition remains in `DashboardPage.tsx`; dashboard-specific presentation components are intentionally focused: `OperationalMetric`, `FlightOperationsTable`, `CrewAvailabilityPanel`, `OperationalAlerts`, `RecentActivity`, and `OperationalCharts`.

The UTC date and all data surfaces explicitly identify themselves as demo/sample material. Figures do not claim to be backend or live operations data. The dashboard uses the established navy, teal, canvas, white, ink, muted, and border palette. Charts are accessible labelled bar indicators; no chart dependency was added.

## Interactions and responsive behavior

The working flight-view select filters the table between all, scheduled-only, and attention-required sample flights, including its empty state. The page uses a five-column desktop metric strip that steps down to two columns; operations and information panels stack on narrower viewports; tables retain a deliberate horizontal-scroll container for small screens. Existing mobile overlay navigation remains unchanged.

## Testing

Vitest, jsdom, and React Testing Library are configured in `vite.config.ts` and `src/test/setup.ts`. Tests cover dashboard rendering, metric/flight/alert rendering, working flight filtering, and the flight-table empty state.

```powershell
cd frontend
npm run lint
npm run test
npm run build
```

## Limitations and next phase

There is no live API integration, CRUD, scheduling action, calendar, reporting backend, or authentication. The next recommended step is a reviewed Phase 3.3 read-only API integration, preserving same-origin security and frozen OpenAPI/version-precondition requirements.
