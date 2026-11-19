# Phase 3.10 - Live backend integration blocker

## Outcome

No React page was connected to the backend in this phase. The pre-integration security audit identified a blocking deployment-topology requirement that cannot be safely resolved within the frontend-only scope.

## Verified constraint

`ApiCorsFilter` applies to every `/api/v1` request. It permits only loopback API hosts and rejects any browser request that `CorsUtils.isCorsRequest` identifies as cross-origin, returning `403 ORIGIN_NOT_ALLOWED` and the message `Use the API from the same origin`.

The Vite development server uses a distinct origin (for example `http://127.0.0.1:5173`) from the backend (`http://127.0.0.1:8080`). Therefore direct Axios requests are cross-origin and rejected. A Vite proxy which forwards or rewrites the browser `Origin` would conceal/spoof the security boundary, which the project rules and Phase 3.1 documentation explicitly prohibit. The API has no documented health endpoint; the existing Phase 3.9 reachability probe must not be used to bypass this restriction.

## Required owner decision before live integration

Use one of these owner-approved, backend-coordinated approaches:

1. Serve the built React files from the existing backend origin, with history-route fallback arranged by the backend owner; or
2. Introduce an approved reverse-proxy/deployment topology that preserves an actual same-origin browser request and is reviewed with the backend owner.

Neither approach is implementable by changing only the React pages without altering backend/static hosting or the agreed deployment architecture. No wildcard CORS, Origin rewrite, host-binding change, security-header spoofing, or frontend-only proxy was introduced.

## Contract audit notes

The Phase 3.9 services match the frozen OpenAPI 1.1.0 operation paths and conditional-mutation set. The audit also confirms that backend API contracts use `COPILOT`, while completed demo pages use `CO_PILOT`; Phase 3.9 intentionally separated these as `BackendCrewRole` and demo `CrewRole` to avoid breaking existing screens before a live-data migration.

Conditional operations retain original loaded `id:version` values and send `X-Expected-Version` only for the thirteen documented mutations. In particular, qualification expiry/revocation URLs use the qualification path identity while the expected-version token uses the held-record `CrewQualificationView.id` and version.

## Verification and limitations

No real database-backed workflow, Axios browser request, live CRUD action, or page migration was attempted because it would fail the existing same-origin control or require a prohibited workaround. Existing demo screens and all frontend service code remain unchanged by Phase 3.10. Browser visual verification was not performed.

After the owner supplies an approved same-origin topology, rerun the Phase 3.10 integration prompt beginning with a connectivity check, then integrate one module at a time with the existing typed services, normalized errors, and version-token handling.
