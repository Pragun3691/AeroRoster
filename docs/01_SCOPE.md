# Project scope (approved provisional design)

## Goal

AeroRoster is an educational web app for an airline admin to manage a small fleet and its crew while preventing invalid schedules.

## Primary users

- Operations admin: manages crew, airports, aircraft, flights and assignments.
- Read-only viewer/crew view may be added after core workflows, if course allows.

## Mandatory functional flows

1. Create/search/update/disable crew; record their qualifications and expiry.
2. Create/search/update flight instances with airports, aircraft, UTC times and status.
3. Assign or unassign crew after server-side checking of role and valid qualification, current unavailability, time overlap, rest interval and illustrative duty-hour policy.
4. Show reasoned rejection messages; never write a partial assignment.
5. Daily/weekly roster; duty hours and missing-crew reports.
6. Suggest qualified candidates ranked by lowest recent duty load; admin must confirm any assignments.

## Quality

- Java-centered OOP: records, enums, classes, services, repositories, interfaces and exceptions.
- PostgreSQL integrity constraints, indexes and versioned schema migrations.
- Tests: valid/invalid scheduling, boundary times, expired qualifications, concurrent assignment attempts.
- Professionally styled, practical responsive UI; meaningful demo data.
- Versioned audit trail on flight changes and later duty assignment actions.

## Out of scope

- Passenger ticketing, payments, check-in, live aviation data, AI models, official/legal duty compliance.
- Multi-airline tenancy and complex rostering optimization.

## Completion definition

A fresh-machine user can start the app, access the UI, create a crew member and a flight, assign valid crew, see conflict rejection, view a roster, and run automated tests.
