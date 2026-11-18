package com.aeroroster.service;

import com.aeroroster.persistence.entity.*;
import com.aeroroster.persistence.repository.*;
import com.aeroroster.service.dto.*;
import com.aeroroster.service.exception.*;
import com.aeroroster.support.FixedClockConfiguration;
import com.aeroroster.support.PostgresTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@PostgresTest
@Import(FixedClockConfiguration.class)
@Transactional
class CoreServicesIntegrationTest {
    private static final PageRequest PAGE = PageRequest.of(0, 50);
    private static final Long MISSING = Long.MAX_VALUE;

    @Autowired CrewService crewService;
    @Autowired AirportService airportService;
    @Autowired AircraftTypeService typeService;
    @Autowired AircraftService aircraftService;
    @Autowired QualificationService qualificationService;
    @Autowired FlightService flightService;
    @Autowired ServiceTransactions transactions;
    @Autowired CrewRepository crew;
    @Autowired AircraftRepository aircraft;
    @Autowired AircraftTypeRepository types;
    @Autowired AirportRepository airports;
    @Autowired FlightRepository flights;
    @Autowired QualificationRepository qualifications;
    @Autowired DutyAssignmentRepository assignments;
    @Autowired FlightAuditRepository audits;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    private CrewRequest crewRequest(String license, CrewRole role) {
        return new CrewRequest(" Test Member ", role, license, 3, "+91 9000000000");
    }

    private CrewView newCrew(String license, CrewRole role) {
        return crewService.create(crewRequest(license, role));
    }

    private Long typeId(String model) {
        return types.findByModel(model).orElseThrow().getId();
    }

    private Long planeId() {
        return aircraft.findByRegistration("VT-AR101").orElseThrow().getId();
    }

    private Long airportId(String code) {
        return airports.findByIataCode(code).orElseThrow().getId();
    }

    private Long requirement(CrewRole role) {
        return qualifications.findByAircraftTypeIdAndRequiredRole(typeId("Airbus A320"), role).orElseThrow().getId();
    }

    private FlightRequest flightRequest(String number, Long plane, String departure, String arrival) {
        return new FlightRequest(number, airportId("DEL"), airportId("BOM"), plane,
                Instant.parse(departure), Instant.parse(arrival));
    }

    private FlightRequest futureRequest(String number) {
        return flightRequest(number, planeId(), "2026-12-10T05:00:00Z", "2026-12-10T07:00:00Z");
    }

    private FlightView seededAssignedFlight() {
        return flightService.search(new FlightFilter("AI-101", null, null,
                Instant.parse("2026-12-01T00:00:00Z"), Instant.parse("2026-12-02T00:00:00Z")), PAGE)
                .getContent().getFirst();
    }

    @Test
    void crewCreationUpdateAndFiltersReturnDetachedRecords() {
        CrewView created = newCrew(" t22-pilot ", CrewRole.PILOT);
        assertEquals("Test Member", created.name());
        assertEquals("T22-PILOT", created.licenseNo());
        assertTrue(created.active());
        CrewView updated = crewService.update(created.id(),
                new CrewRequest("Updated Name", CrewRole.PILOT, "T22-UPDATED", 4, "9000000000"));
        entityManager.clear();
        assertEquals(updated, crewService.get(created.id()));
        assertEquals(1, crewService.search(new CrewFilter("updated", CrewRole.PILOT, true), PAGE).getTotalElements());
        assertEquals(0, crewService.search(new CrewFilter("updated", CrewRole.CABIN_CREW, true), PAGE).getTotalElements());
        assertFalse(crewService.setActive(created.id(), false).active());
        assertEquals(1, crewService.search(new CrewFilter("updated", null, false), PAGE).getTotalElements());
        assertTrue(crewService.setActive(created.id(), true).active());
    }

    @Test
    void crewDuplicateLicenseRejectedOnCreate() {
        assertThrows(DuplicateResourceException.class, () -> newCrew(" demo-p-001 ", CrewRole.PILOT));
    }

    @Test
    void crewDuplicateLicenseRejectedOnUpdate() {
        CrewView member = newCrew("T22-UNIQUE", CrewRole.PILOT);
        assertThrows(DuplicateResourceException.class,
                () -> crewService.update(member.id(), crewRequest("DEMO-P-001", CrewRole.PILOT)));
        assertEquals("T22-UNIQUE", crewService.get(member.id()).licenseNo());
    }

    @Test
    void invalidCrewInputsAndMissingRecordsHaveTypedErrors() {
        assertThrows(InvalidInputException.class, () -> crewService.create(null));
        assertThrows(InvalidInputException.class, () -> crewService.create(
                new CrewRequest(" ", null, " ", -1, "abc")));
        assertThrows(InvalidInputException.class, () -> crewService.get(0L));
        assertThrows(ResourceNotFoundException.class, () -> crewService.get(MISSING));
        assertThrows(ResourceNotFoundException.class, () -> crewService.update(MISSING, crewRequest("T22-MISSING", CrewRole.PILOT)));
        assertThrows(ResourceNotFoundException.class, () -> crewService.setActive(MISSING, false));
        assertThrows(ResourceNotFoundException.class, () -> crewService.assignments(MISSING));
    }

    @Test
    void crewDeactivationProtectsFutureActiveAssignments() {
        Long id = crew.findByLicenseNo("DEMO-P-001").orElseThrow().getId();
        assertThrows(BusinessConflictException.class, () -> crewService.setActive(id, false));
        assertTrue(crewService.get(id).active());
        assertEquals(1, crewService.assignments(id).size());
    }

    @Test
    void crewRoleChangeProtectsQualificationsAndAssignmentHistory() {
        Long id = crew.findByLicenseNo("DEMO-P-001").orElseThrow().getId();
        assertThrows(BusinessConflictException.class, () -> crewService.update(id, crewRequest("DEMO-P-001", CrewRole.COPILOT)));
        assertEquals(CrewRole.PILOT, crewService.get(id).role());
    }

    @Test
    void referenceServicesReadAndSearchWithoutEntityContracts() {
        assertEquals(5, airportService.search(null).size());
        assertEquals("DEL", airportService.search("new delhi").getFirst().iataCode());
        assertEquals("DEL", airportService.get(airportId("DEL")).iataCode());
        assertTrue(airportService.search("%").isEmpty());
        assertEquals(2, typeService.list().size());
        assertEquals(2, typeService.get(typeId("Airbus A320")).minCabinCrew());
        assertEquals(6, qualificationService.requirements().size());
        assertThrows(ResourceNotFoundException.class, () -> airportService.get(MISSING));
        assertThrows(ResourceNotFoundException.class, () -> typeService.get(MISSING));
    }

    @Test
    void aircraftRegistrationUpdateAndActivityFilters() {
        AircraftView plane = aircraftService.register(new AircraftRequest(" t22-plane ", typeId("Airbus A320")));
        assertEquals("T22-PLANE", plane.registration());
        AircraftView updated = aircraftService.update(plane.id(), new AircraftRequest("T22-RENAMED", typeId("Boeing 737-800")));
        assertEquals("Boeing 737-800", updated.model());
        assertFalse(aircraftService.setActive(plane.id(), false).active());
        assertEquals(1, aircraftService.search(new AircraftFilter("renamed", false, updated.aircraftTypeId()), PAGE).getTotalElements());
        assertEquals(0, aircraftService.search(new AircraftFilter("renamed", true, null), PAGE).getTotalElements());
        assertTrue(aircraftService.setActive(plane.id(), true).active());
        entityManager.clear();
        assertEquals("T22-RENAMED", aircraftService.get(plane.id()).registration());
    }

    @Test
    void aircraftDuplicateRegistrationRejected() {
        assertThrows(DuplicateResourceException.class,
                () -> aircraftService.register(new AircraftRequest("vt-ar101", typeId("Airbus A320"))));
    }

    @Test
    void aircraftRejectsInvalidTypeAndMissingRecords() {
        assertThrows(ResourceNotFoundException.class, () -> aircraftService.register(new AircraftRequest("T22-BAD", MISSING)));
        assertThrows(ResourceNotFoundException.class, () -> aircraftService.get(MISSING));
        assertThrows(ResourceNotFoundException.class, () -> aircraftService.update(MISSING, new AircraftRequest("T22-BAD", typeId("Airbus A320"))));
        assertThrows(ResourceNotFoundException.class, () -> aircraftService.setActive(MISSING, false));
        assertThrows(InvalidInputException.class, () -> aircraftService.register(new AircraftRequest(" ", null)));
    }

    @Test
    void aircraftDeactivationAndTypeChangesProtectExistingFlights() {
        assertThrows(BusinessConflictException.class, () -> aircraftService.setActive(planeId(), false));
        assertThrows(BusinessConflictException.class,
                () -> aircraftService.update(planeId(), new AircraftRequest("VT-AR101", typeId("Boeing 737-800"))));
        assertTrue(aircraftService.get(planeId()).active());
        assertEquals("Airbus A320", aircraftService.get(planeId()).model());
    }

    @Test
    void qualificationGrantSupportsExpiredAndOpenEndedRecords() {
        CrewView member = newCrew("T22-QUAL", CrewRole.PILOT);
        Long requirement = requirement(CrewRole.PILOT);
        CrewQualificationView granted = qualificationService.grant(member.id(),
                new QualificationGrantRequest(requirement, LocalDate.of(2020, 1, 1)));
        assertEquals(LocalDate.of(2020, 1, 1), granted.validUntil());
        assertEquals("Airbus A320", granted.model());
        assertNull(qualificationService.updateExpiry(member.id(), requirement, new QualificationExpiryRequest(null)).validUntil());
        assertEquals(LocalDate.of(2027, 1, 1), qualificationService.updateExpiry(member.id(), requirement,
                new QualificationExpiryRequest(LocalDate.of(2027, 1, 1))).validUntil());
        qualificationService.revoke(member.id(), requirement);
        assertTrue(qualificationService.forCrew(member.id()).isEmpty());
    }

    @Test
    void qualificationRejectsDuplicateGrant() {
        Long id = crew.findByLicenseNo("DEMO-P-001").orElseThrow().getId();
        assertThrows(DuplicateResourceException.class, () -> qualificationService.grant(id,
                new QualificationGrantRequest(requirement(CrewRole.PILOT), null)));
    }

    @Test
    void qualificationRejectsRoleMismatch() {
        CrewView member = newCrew("T22-MISMATCH", CrewRole.COPILOT);
        assertThrows(BusinessConflictException.class, () -> qualificationService.grant(member.id(),
                new QualificationGrantRequest(requirement(CrewRole.PILOT), null)));
    }

    @Test
    void qualificationRejectsInvalidReferences() {
        Long requirement = requirement(CrewRole.PILOT);
        assertThrows(ResourceNotFoundException.class, () -> qualificationService.grant(MISSING, new QualificationGrantRequest(requirement, null)));
        CrewView member = newCrew("T22-REF", CrewRole.PILOT);
        assertThrows(ResourceNotFoundException.class, () -> qualificationService.grant(member.id(), new QualificationGrantRequest(MISSING, null)));
        assertThrows(ResourceNotFoundException.class, () -> qualificationService.forCrew(MISSING));
        assertThrows(ResourceNotFoundException.class, () -> qualificationService.updateExpiry(member.id(), requirement, new QualificationExpiryRequest(null)));
        assertThrows(ResourceNotFoundException.class, () -> qualificationService.revoke(member.id(), requirement));
    }

    @Test
    void qualificationMutationsPreserveAssignmentHistory() {
        Long id = crew.findByLicenseNo("DEMO-P-001").orElseThrow().getId();
        Long requirement = requirement(CrewRole.PILOT);
        assertThrows(BusinessConflictException.class, () -> qualificationService.revoke(id, requirement));
        assertThrows(BusinessConflictException.class, () -> qualificationService.updateExpiry(id, requirement,
                new QualificationExpiryRequest(LocalDate.of(2026, 11, 30))));
        assertEquals(LocalDate.of(2027, 12, 31), qualificationService.forCrew(id).getFirst().validUntil());
        assertEquals(LocalDate.of(2026, 12, 1), qualificationService.updateExpiry(id, requirement,
                new QualificationExpiryRequest(LocalDate.of(2026, 12, 1))).validUntil());
        assertEquals(LocalDate.of(2028, 12, 31), qualificationService.updateExpiry(id, requirement,
                new QualificationExpiryRequest(LocalDate.of(2028, 12, 31))).validUntil());
    }

    @Test
    void flightsCreateUpdateAndFilterWithAuditTrail() {
        FlightView created = flightService.create(futureRequest(" t22-flight "));
        assertEquals("T22-FLIGHT", created.flightNumber());
        assertEquals(FlightStatus.SCHEDULED, created.status());
        FlightView updated = flightService.update(created.id(), flightRequest("T22-EDIT", planeId(),
                "2026-12-10T06:00:00Z", "2026-12-10T08:00:00Z"));
        entityManager.clear();
        assertEquals(updated, flightService.get(created.id()));
        assertEquals(1, flightService.search(new FlightFilter("t22-edit", FlightStatus.SCHEDULED, planeId(),
                Instant.parse("2026-12-10T06:00:00Z"), Instant.parse("2026-12-10T08:00:00Z")), PAGE).getTotalElements());
        assertEquals(0, flightService.search(new FlightFilter("t22-edit", null, null,
                null, Instant.parse("2026-12-10T06:00:00Z")), PAGE).getTotalElements());
        assertEquals(1, flightService.search(new FlightFilter("t22-edit", null, null, null, null), PAGE).getTotalElements());
        assertEquals(4, flightService.forAircraft(planeId()).size());
        assertEquals(List.of("INSERT", "UPDATE"), audits.findByFlightIdOrderByActionTimeAscIdAsc(created.id())
                .stream().map(FlightAudit::getAction).toList());
    }

    @Test
    void flightsRejectInvalidWindowsAirportsAndSubMicrosecondTimes() {
        Instant departure = Instant.parse("2026-12-10T05:00:00Z");
        assertThrows(InvalidInputException.class, () -> flightService.create(new FlightRequest("T22-BAD", airportId("DEL"), airportId("BOM"), planeId(), departure, departure)));
        assertThrows(InvalidInputException.class, () -> flightService.create(new FlightRequest("T22-BAD", airportId("DEL"), airportId("BOM"), planeId(), departure, departure.minusSeconds(1))));
        assertThrows(InvalidInputException.class, () -> flightService.create(new FlightRequest("T22-BAD", airportId("DEL"), airportId("DEL"), planeId(), departure, departure.plusSeconds(1))));
        assertThrows(InvalidInputException.class, () -> flightService.create(new FlightRequest("T22-BAD", airportId("DEL"), airportId("BOM"), planeId(), departure.plusNanos(1), departure.plusSeconds(1))));
    }

    @Test
    void flightsRejectMissingReferences() {
        FlightRequest request = futureRequest("T22-MISSING");
        assertThrows(ResourceNotFoundException.class, () -> flightService.create(new FlightRequest(request.flightNumber(), MISSING, request.destinationAirportId(), request.aircraftId(), request.departureTime(), request.arrivalTime())));
        assertThrows(ResourceNotFoundException.class, () -> flightService.create(new FlightRequest(request.flightNumber(), request.sourceAirportId(), request.destinationAirportId(), MISSING, request.departureTime(), request.arrivalTime())));
        assertThrows(ResourceNotFoundException.class, () -> flightService.get(MISSING));
        assertThrows(ResourceNotFoundException.class, () -> flightService.update(MISSING, request));
        assertThrows(ResourceNotFoundException.class, () -> flightService.cancel(MISSING));
        assertThrows(ResourceNotFoundException.class, () -> flightService.forAircraft(MISSING));
    }

    @Test
    void flightsRequireActiveAircraft() {
        AircraftView plane = aircraftService.register(new AircraftRequest("T22-INACTIVE", typeId("Airbus A320")));
        aircraftService.setActive(plane.id(), false);
        assertThrows(BusinessConflictException.class, () -> flightService.create(flightRequest("T22-INACTIVE", plane.id(), "2026-12-10T05:00:00Z", "2026-12-10T07:00:00Z")));
        FlightView existing = flightService.create(futureRequest("T22-ACTIVE"));
        assertThrows(BusinessConflictException.class, () -> flightService.update(existing.id(), flightRequest("T22-ACTIVE", plane.id(), "2026-12-10T05:00:00Z", "2026-12-10T07:00:00Z")));
    }

    @Test
    void unstaffedFlightCanMoveBetweenAircraftInEitherIdOrder() {
        FlightView flight = flightService.create(futureRequest("T22-MOVE"));
        Long other = aircraft.findByRegistration("VT-AR102").orElseThrow().getId();
        assertEquals(other, flightService.update(flight.id(), flightRequest("T22-MOVE", other,
                "2026-12-10T05:00:00Z", "2026-12-10T07:00:00Z")).aircraftId());
        assertEquals(planeId(), flightService.update(flight.id(), futureRequest("T22-MOVE")).aircraftId());
    }

    @Test
    void pastDutiesAndPastFlightsPermitDeactivation() {
        AircraftView plane = aircraftService.register(new AircraftRequest("T22-PAST-PLANE", typeId("Airbus A320")));
        FlightView flight = flightService.create(flightRequest("T22-PAST-DUTY", plane.id(),
                "2026-11-17T05:00:00Z", "2026-11-17T07:00:00Z"));
        CrewView member = newCrew("T22-PAST-PILOT", CrewRole.PILOT);
        DutyAssignment assignment = new DutyAssignment();
        assignment.setCrew(crew.getReferenceById(member.id()));
        assignment.setFlight(flights.getReferenceById(flight.id()));
        assignment.setAssignedRole(CrewRole.PILOT);
        assignment.setDutyStartTime(Instant.parse("2026-11-17T04:00:00Z"));
        assignment.setDutyEndTime(Instant.parse("2026-11-17T07:30:00Z"));
        assignments.saveAndFlush(assignment);
        assertFalse(crewService.setActive(member.id(), false).active());
        assertFalse(aircraftService.setActive(plane.id(), false).active());
    }

    @Test
    void duplicateFlightInstanceRejectedEvenOnAnotherAircraft() {
        flightService.create(futureRequest("T22-DUP"));
        Long other = aircraft.findByRegistration("VT-AR102").orElseThrow().getId();
        assertThrows(DuplicateResourceException.class, () -> flightService.create(flightRequest("t22-dup", other, "2026-12-10T05:00:00Z", "2026-12-10T07:00:00Z")));
    }

    @Test
    void aircraftOverlapRejected() {
        flightService.create(futureRequest("T22-FIRST"));
        assertThrows(BusinessConflictException.class, () -> flightService.create(flightRequest("T22-OVERLAP", planeId(), "2026-12-10T06:59:59Z", "2026-12-10T08:00:00Z")));
    }

    @Test
    void adjacentFlightsAreAllowedAndCancelledFlightReleasesAircraft() {
        FlightView first = flightService.create(futureRequest("T22-FIRST"));
        FlightView next = flightService.create(flightRequest("T22-NEXT", planeId(), "2026-12-10T07:00:00Z", "2026-12-10T09:00:00Z"));
        assertNotNull(next.id());
        assertEquals(FlightStatus.CANCELLED, flightService.cancel(first.id()).status());
        assertNotNull(flightService.create(futureRequest("T22-REUSE")).id());
    }

    @Test
    void activeAssignmentsBlockFlightUpdatesAndCancellationWithoutSideEffects() {
        FlightView assigned = seededAssignedFlight();
        long beforeAudits = audits.count();
        FlightRequest same = new FlightRequest(assigned.flightNumber(), assigned.sourceAirportId(), assigned.destinationAirportId(), assigned.aircraftId(), assigned.departureTime(), assigned.arrivalTime());
        assertEquals(assigned, flightService.update(assigned.id(), same));
        assertThrows(BusinessConflictException.class, () -> flightService.update(assigned.id(), new FlightRequest(assigned.flightNumber(), assigned.sourceAirportId(), assigned.destinationAirportId(), assigned.aircraftId(), assigned.departureTime().plusSeconds(60), assigned.arrivalTime().plusSeconds(60))));
        assertThrows(BusinessConflictException.class, () -> flightService.cancel(assigned.id()));
        assertEquals(assigned, flightService.get(assigned.id()));
        assertEquals(4, assignments.findByFlightIdAndStatus(assigned.id(), AssignmentStatus.ACTIVE).size());
        assertEquals(beforeAudits, audits.count());
    }

    @Test
    void unsafeAircraftSwitchIsRejectedAndOriginalFlightRetained() {
        FlightView first = flightService.create(futureRequest("T22-SWITCH"));
        Long other = aircraft.findByRegistration("VT-AR102").orElseThrow().getId();
        flightService.create(flightRequest("T22-OTHER", other, "2026-12-10T05:00:00Z", "2026-12-10T07:00:00Z"));
        assertThrows(BusinessConflictException.class, () -> flightService.update(first.id(), flightRequest("T22-SWITCH", other, "2026-12-10T05:00:00Z", "2026-12-10T07:00:00Z")));
        assertEquals(planeId(), flightService.get(first.id()).aircraftId());
    }

    @Test
    void statusTransitionsAreTerminalAndCompletionUsesClock() {
        FlightView future = flightService.create(futureRequest("T22-FUTURE"));
        assertThrows(BusinessConflictException.class, () -> flightService.changeStatus(future.id(), FlightStatus.COMPLETED));
        FlightView past = flightService.create(flightRequest("T22-PAST", planeId(), "2026-11-17T05:00:00Z", "2026-11-17T07:00:00Z"));
        assertEquals(FlightStatus.COMPLETED, flightService.changeStatus(past.id(), FlightStatus.COMPLETED).status());
        assertEquals(FlightStatus.COMPLETED, flightService.changeStatus(past.id(), FlightStatus.COMPLETED).status());
        assertThrows(BusinessConflictException.class, () -> flightService.changeStatus(past.id(), FlightStatus.SCHEDULED));
        assertThrows(BusinessConflictException.class, () -> flightService.cancel(past.id()));
        FlightView cancelled = flightService.cancel(future.id());
        assertEquals(cancelled, flightService.cancel(future.id()));
        assertThrows(BusinessConflictException.class, () -> flightService.changeStatus(future.id(), FlightStatus.SCHEDULED));
        assertThrows(BusinessConflictException.class, () -> flightService.update(future.id(), futureRequest("T22-FUTURE")));
    }

    @Test
    void searchTreatsWildcardsAsLiteralAndValidatesFilters() {
        assertEquals(0, crewService.search(new CrewFilter("%", null, null), PAGE).getTotalElements());
        assertEquals(0, flightService.search(new FlightFilter("_", null, null, null, null), PAGE).getTotalElements());
        assertThrows(InvalidInputException.class, () -> flightService.search(new FlightFilter(null, null, null, Instant.parse("2026-12-02T00:00:00Z"), Instant.parse("2026-12-01T00:00:00Z")), PAGE));
        assertThrows(InvalidInputException.class, () -> crewService.search(new CrewFilter(null, null, null), null));
        assertThrows(InvalidInputException.class, () -> aircraftService.search(new AircraftFilter(null, null, -1L), PAGE));
    }

    @Test
    void databaseUniquenessFailureIsTranslatedToBusinessException() {
        DuplicateResourceException exception = assertThrows(DuplicateResourceException.class,
                () -> transactions.write(() -> jdbc.update("insert into aircraft (registration, aircraft_type_id) values (?, ?)", "VT-AR101", typeId("Airbus A320"))));
        assertEquals("A record with these unique identifiers already exists", exception.getMessage());
    }
}
