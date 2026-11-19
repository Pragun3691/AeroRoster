# Phase 3.10A - Same-origin deployment architecture

## Architecture

The React application is built by Maven and copied into `target/classes/static`, so the packaged Spring Boot JAR serves the SPA and `/api/v1` from the same loopback origin. Browser API configuration defaults to the relative `/api/v1` path; it never hardcodes an API hostname in production.

## Reproducible build

At Maven `generate-resources`, `exec-maven-plugin` executes from `frontend/`:

```text
npm ci
npm run build -- --outDir ../target/classes/static
```

The lockfile governs frontend dependencies. Vite uses `base: '/'`, producing root-relative fingerprinted assets under `assets/`. Generated `target/`, `frontend/node_modules/`, and `frontend/dist/` are not committed.

## Static serving and history fallback

Spring's normal static-resource handler serves `index.html` and hashed assets. `HomeController` forwards `/` to `index.html`. `SpaHistoryController` forwards only the known application routes and numeric detail-route forms; it does not map `/api/**`, `/api-docs/**`, static assets, actuator paths, or unknown routes. Consequently missing JavaScript, CSS, image and font paths are left to Spring's normal 404 handling rather than receiving HTML.

## Security and API behavior

`ApiCorsFilter` is unmodified. The SPA is served at the same `127.0.0.1:8080` origin as the API, so browser requests to `/api/v1/...` are genuine same-origin requests. No Vite proxy, Origin/Host rewrite, wildcard CORS, host-binding change, or filter bypass exists. The backend remains unauthenticated local-development software; no authorization implementation exists. No CSRF configuration exists in the inspected application, and no claim of CSRF protection is made.

## Verification results

On 19 November 2026, a clean `npm ci` was attempted. Its first attempt encountered Windows' transient `ENOTEMPTY` cleanup error in `node_modules/lucide-react`; after removing only that generated directory and rerunning `npm ci`, the locked dependencies were restored. `npm run lint` passed. `npm run test` passed: 9 files, 43 tests. `npm run build` passed and emitted `dist/index.html` plus root-relative fingerprinted `/assets/index-BOdTM7K-.css` and `/assets/index-D-12MiGc.js`.

Maven and a Maven wrapper were unavailable in the execution environment (`mvn` was not recognized), and no local `DB_*` configuration was present. Therefore Maven packaging, backend/JUnit regression tests, packaged-JAR startup, direct same-origin browser-route checks, API isolation checks against the running JAR, and database-backed checks were **not performed**. The source-level `SpaHistoryControllerTest` covers every approved route forward plus API and missing asset-like route non-interception; it must be run by the owner with Maven available. Existing `LocalApiSecurityRegressionTest` remains the regression coverage for origin/host rejection.

When Maven is available, run `mvn package` then start the JAR with the documented local database settings. Confirm direct GET/refresh navigation for every listed SPA route, `/api/v1/airports`, a missing API path, existing fingerprinted CSS/JS, and missing asset paths.

## Limitations

Phase 3.10A does not call services from operational pages or perform CRUD integration. It does not change any API contract, database/migration, scheduling rule, authentication, or backend API security policy. Phase 3.10 may start only after this deployment architecture is reviewed and its packaged-JAR verification is completed.
