# Existing Oracle repository audit — 18 Nov 2026

Public archive: https://github.com/Pragun3691/airline-crew-dbms

Main SQL: https://github.com/Pragun3691/airline-crew-dbms/blob/main/airline_crew_livesql.sql

Observed seven core table definitions: Airport, Aircraft, Crew, Flight, Qualification, Crew_Qualification, Duty_Assignment. A separate Flight_Audit table is created later (eight definitions total). README claims nine; treat that as a README mismatch.

Reusable domain model: crew members and roles, flights and assigned aircraft, crew qualifications, duty assignments, airport seed data, reports.

Observed PL/SQL functions/procedures: sp_add_crew, sp_assign_duty, sp_flight_crew, fn_total_duty_hours, fn_is_available; triggers for max daily duty hours and Flight_Audit.

Required fixes:
- Aircraft.model is unique; separate AircraftType (model) from individual aircraft registrations.
- Flight.flightNumber unique disallows same flight number on different dates. Enforce (flight_number, departure_time) instead.
- Flight.source/destination are text and not linked to Airport. Use FKs and IATA code.
- Availability by date alone is too coarse; use interval overlap and rest logic.
- Oracle row-level trg_max_daily_hours reads Duty_Assignment during its INSERT; likely ORA-04091 mutating-table failure. Move validation to transactional Java service and treat the DB as enforcing structural rules.
- Qualification needs an aircraft type FK, with optional expiry.
- DutyHours accepted as arbitrary input; derive duty window/duration from flight schedule, storing an assignment snapshot.
- Original sample flight timestamps are in May/June 2026; new demo dates use December 2026.

Important: This audit was based on public source inspection. Runtime behavior of the old Oracle script was not executed.
