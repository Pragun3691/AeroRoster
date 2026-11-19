# Phase 3.6 — Qualifications Management UI

## Implemented UI

- `/qualifications`: demo-only compliance register with summary counts; search; crew-role, aircraft-type and status filters; crew/expiry sorting; responsive table; no-result state; aircraft-type requirement reference.
- `/qualifications/:qualificationId`: read-only held qualification detail.
- Local-only grant, expiry-review, and revoke-review flows with validation and confirmation. None sends an HTTP request or alters demo data.

## Contract inspected

Frozen OpenAPI 1.1.0 and the Java DTOs/controllers define:

- `GET /qualifications` and `GET /aircraft-types/{aircraftTypeId}/qualifications` returning `QualificationView(id, aircraftTypeId, model, requiredRole)`.
- `GET/POST /crew/{crewId}/qualifications`; POST accepts `QualificationGrantRequest(qualificationId, validUntil)` where only positive `qualificationId` is required.
- `GET /crew/{crewId}/qualifications/{qualificationId}` returning `CrewQualificationView(id, crewId, qualificationId, aircraftTypeId, model, requiredRole, validUntil, version)`.
- `PUT /crew/{crewId}/qualifications/{qualificationId}/expiry` accepts required `ExpiryUpdateRequest.validUntil`; it is a conditional mutation requiring the original held-record `X-Expected-Version: id:version` token.
- `DELETE /crew/{crewId}/qualifications/{qualificationId}` is conditional and only succeeds where no linked assignment history exists.

The UI deliberately does not call these endpoints. Future integration must retain the original held-link version, use the held qualification ID rather than the requirement ID for expiry/revoke, and surface 409/428 responses without replacing a stale client token.

## Demo data and statuses

`qualificationData.ts` contains strictly typed local representations of requirement, crew reference, and held qualification views. A missing qualification is generated only where a crew member lacks a matching role/type requirement; it is distinct from an expired held qualification.

Eligibility’s backend rule is retained: a null expiry is valid, and an expiry on the UTC flight departure date is valid. The demo operating date is 2026-11-18. “Expiring soon” means on/before a labelled local review horizon of 2026-12-18; it is a display prioritisation only, never an eligibility rule.

## Validation and limitations

Grant validates crew selection and the required positive qualification selection. Expiry review requires a date because the API wrapper marks `validUntil` required. Role matching, duplicate prevention, shortening expiry across historical assignments, and revocation-history checks remain backend-owned. Revoke is intentionally a confirmation review that retains the visible record.

No backend Java, SQL migration, OpenAPI, security, API CRUD, assignment, or scheduling changes are included.

## Verification

Run in `frontend/`:

```text
npm run lint
npm run test
npm run build
```

The test module covers page/metrics rendering, filters, sort/no-result state, valid/expired/missing displays, detail route, grant/expiry validation, and revoke confirmation. Route smoke checks and browser-verification status are recorded in the Phase 3.6 completion report.
