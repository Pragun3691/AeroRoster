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

### Finalization evidence - 19 November 2026

The earlier Maven/runtime verification limitation has been superseded by the owner's successful full Maven test/package runs and launch of the packaged application at `http://127.0.0.1:8080`. Those runs were reported by the owner, not rerun during this finalization. Inspection of the existing `target/surefire-reports/TEST-*.xml` found 20 suites, 190 tests, zero failures, zero errors and zero skips. These include `SpaHistoryControllerTest` (2 tests), `LocalApiSecurityRegressionTest` (4 tests) and `FreshMigrationVerificationTest` (1 test). The latter records a successful fresh migration/schema validation check; no database or migration changes were made here.

The checked branch was `review/frontend`; the working tree was clean before this documentation update. No branch change, commit, push or merge was performed.

Independent, read-only HTTP checks against the owner's running application confirmed:

| Check | Observed result |
| --- | --- |
| `/` | 200 `text/html`; response bytes equal `BOOT-INF/classes/static/index.html` in `target/aeroroster-0.1.0-SNAPSHOT.jar` |
| `/dashboard`, `/crew`, `/aircraft`, `/flights`, `/qualifications`, `/assignments`, `/unavailability` | Each returns 200 HTML identical to the packaged SPA index |
| `/crew/1`, `/aircraft/1`, `/flights/1`, `/qualifications/1`, `/assignments/1`, `/unavailability/1` | Each returns 200 HTML identical to the packaged SPA index |
| `/assets/index-D-12MiGc.js` | 200 `text/javascript`, 271,929 bytes; byte-for-byte match to the JAR asset |
| `/assets/index-BOdTM7K-.css` | 200 `text/css`, 21,470 bytes; byte-for-byte match to the JAR asset |
| `/api/v1/airports`, without credentials or Origin | 200 JSON; owner also confirmed real database records |
| `/api/v1/not-a-route`, `/assets/missing.js`, `/assets/missing.css` | Each returns 404 JSON, `NOT_FOUND`, rather than SPA HTML |
| `/not-a-route`, `/crew/not-numeric` | Each returns 404 JSON, `NOT_FOUND` |
| Airports GET with Origin `http://127.0.0.1:8080` | 200 JSON |
| Airports GET with Origin `http://127.0.0.1:5173`, `http://localhost:8080`, or `null` | Each returns 403 JSON, `ORIGIN_NOT_ALLOWED` |
| Airports OPTIONS with Origin `http://127.0.0.1:5173` and requested method POST | 403 JSON, `ORIGIN_NOT_ALLOWED`; no write submitted |
| Airports GET with Host `rebind.example:8080`, with and without matching foreign Origin | Both return 403 JSON, `HOST_NOT_ALLOWED` |

No checked response included an `Access-Control-Allow-Origin` header. These observations establish HTTP serving and fallback behavior, not browser execution of React or CSS rendering.

### Security review

Source review of `pom.xml`, application configuration and backend Java found no Spring Security dependency, authentication/authorization implementation, or CSRF configuration. The successful credential-free airports GET is consistent with the existing unauthenticated local-development design; it does not establish write authorization. `ApiCorsFilter` applies to `/api/v1` and its descendants, restricts Host to localhost/numeric loopback, rejects cross-origin or malformed Origin values, and permits requests without Origin. It is an origin policy, not authentication or a complete CSRF protection mechanism. No authenticated session/CSRF-token flow was available to verify. No writes or security changes were performed.

The Maven production build supplies relative `/api/v1`; the frontend configuration retains that same-origin default. Application binding still defaults to `127.0.0.1`. The filter, security configuration, Java business rules, database migrations and OpenAPI were not modified.

Phase 3.10A does not newly implement authentication, authorization or CSRF protection. Existing origin/host restrictions remain in place.

### Owner-confirmed browser verification - 19 November 2026

The project owner explicitly confirmed manually opening and testing the packaged application at `http://127.0.0.1:8080`, with an **overall browser verification result of PASS**. The following results are attributed to the owner; they were not independently observed by the assistant:

| Check | Owner-confirmed result |
| --- | --- |
| Dashboard rendering | PASS: the AeroRoster dashboard displays correctly with its styling and sidebar |
| Frontend page navigation | PASS: frontend page navigation works |
| Direct navigation | PASS: direct navigation works |
| Page refresh | PASS: page refresh works |
| Visible application failures | None encountered by the owner |
| Browser name and exact version | Not recorded |
| Individual browser-tested route URLs | Not enumerated in the owner's confirmation; no per-route browser pass is inferred |
| Browser DevTools Console / Network | No specific observations supplied; no claim of an error-free Console or browser Network status inspection |

The owner also referenced the previously verified successful JavaScript/CSS asset serving, API routing and existing origin restrictions. Asset HTTP statuses, MIME types and byte-for-byte JAR matches remain supported by the independent HTTP evidence above. The owner's confirmation of the styled, working dashboard supports browser rendering and execution at the reported level; it does not add specific DevTools observations.

During the earlier independent browser attempt, the browser access host was unavailable and native Chrome inspection stopped because Computer Use could not determine the current browser URL confidently enough to enforce policy. The owner's subsequent manual PASS supplies the browser behavior evidence for closure; no independent assistant browser pass is claimed.

### Closure assessment

**Phase 3.10A is complete.** The available evidence satisfies the same-origin deployment acceptance criteria:

| Acceptance criterion | Supporting evidence |
| --- | --- |
| Frontend production build and packaged application launch | Recorded frontend lint/test/build results; owner-confirmed full Maven test/package and JAR launch |
| Unit/regression tests and database migration check | Existing Surefire reports: 190 tests, zero failures/errors/skips, including SPA route, local API security and fresh migration/schema verification |
| React rendering, page navigation, direct navigation and refresh | Explicit owner-confirmed browser PASS at `http://127.0.0.1:8080` |
| Packaged index and actual JavaScript/CSS asset serving | Independent HTTP 200 responses with appropriate MIME types and byte-for-byte JAR matches |
| Approved history-route forms and API/missing-asset isolation | Independent HTTP route checks documented above |
| Existing security restrictions preserved | Recorded origin/host rejection checks and source review; no security changes |
| Status and verification evidence documented | This report, including attribution and evidence limitations |

Browser name/version, a per-route browser execution record and specific DevTools observations were not recorded. These are evidence-detail limitations, not remaining functional acceptance blockers given the owner's explicit behavior confirmations and the existing route/asset HTTP evidence. No browser-tested URL beyond the reported application origin is invented, and no assertion is made that every listed route was individually exercised in the browser.

Authentication, authorization and CSRF protection are not newly implemented by this phase. The existing local-development security limitations remain as documented above. No security, backend business logic, SQL migration or OpenAPI change was made during closure. No tests or browser checks were rerun for this documentation-only closure; it uses the existing verified results and the owner's confirmation. No Git commands were run during this closure update.

Work stops at Phase 3.10A. Phase 3.10 live frontend API integration has not begun and is not authorized by this closure.

## Limitations

Phase 3.10A does not call services from operational pages or perform CRUD integration. It does not change any API contract, database/migration, scheduling rule, authentication, or backend API security policy. Phase 3.10 may start only after this deployment architecture is reviewed and its packaged-JAR verification is completed.
