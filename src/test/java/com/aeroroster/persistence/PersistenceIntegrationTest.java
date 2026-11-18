package com.aeroroster.persistence;

import com.aeroroster.persistence.entity.*;
import com.aeroroster.persistence.repository.*;
import jakarta.persistence.EntityManager;
import com.aeroroster.support.PostgresTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL, real Flyway migrations, and rollback after each test. */
@PostgresTest
@Transactional
class PersistenceIntegrationTest {
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired AirportRepository airports;
    @Autowired AircraftTypeRepository aircraftTypes;
    @Autowired AircraftRepository aircraft;
    @Autowired CrewRepository crew;
    @Autowired QualificationRepository qualifications;
    @Autowired CrewQualificationRepository crewQualifications;
    @Autowired FlightRepository flights;
    @Autowired DutyAssignmentRepository assignments;
    @Autowired CrewUnavailabilityRepository unavailability;
    @Autowired FlightAuditRepository audits;

    private static Instant instant(String value) {
        return Instant.parse(value);
    }

    private Crew pilot() {
        return crew.findByLicenseNo("DEMO-P-001").orElseThrow();
    }

    private Flight firstFlight() {
        return flights.findDepartingInRange(instant("2026-12-01T05:00:00Z"),
                instant("2026-12-01T05:00:01Z")).getFirst();
    }

    @Test
    void migrationsAndSeedMapAllTenTables() {
        assertEquals(3, jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class));
        assertEquals(5, airports.count());
        assertEquals(2, aircraftTypes.count());
        assertEquals(3, aircraft.count());
        assertEquals(12, crew.count());
        assertEquals(6, qualifications.count());
        assertEquals(12, crewQualifications.count());
        assertEquals(5, flights.count());
        assertEquals(4, assignments.count());
        assertEquals(1, unavailability.count());
        assertEquals(5, audits.count());
        assertEquals(10, entityManager.getMetamodel().getEntities().size());
    }

    @Test
    void crewAndFleetQueriesRespectRoleAndActiveFlag() {
        assertEquals(12, crew.findByActiveTrue().size());
        for (CrewRole role : CrewRole.values()) {
            int expected = role == CrewRole.CABIN_CREW ? 6 : 3;
            assertEquals(expected, crew.findByRole(role).size());
            assertEquals(expected, crew.findByActiveTrueAndRole(role).size());
        }
        Crew pilot = pilot();
        pilot.setActive(false);
        crew.flush();
        assertEquals(11, crew.findByActiveTrue().size());
        assertEquals(2, crew.findByActiveTrueAndRole(CrewRole.PILOT).size());
        AircraftType type = aircraftTypes.findByModel("Airbus A320").orElseThrow();
        assertEquals(180, type.getCapacity());
        assertEquals(2, type.getMinCabinCrew());
        assertEquals(2, aircraft.findByAircraftTypeId(type.getId()).size());
        Aircraft plane = aircraft.findByRegistration("VT-AR101").orElseThrow();
        plane.setActive(false);
        aircraft.flush();
        assertEquals(2, aircraft.findByActiveTrue().size());
    }

    @Test
    void qualificationRelationshipsAndNullableExpiryRoundTrip() {
        Crew pilot = pilot();
        CrewQualification held = crewQualifications.findByCrewId(pilot.getId()).getFirst();
        assertEquals(LocalDate.of(2027, 12, 31), held.getValidUntil());
        Qualification qualification = held.getQualification();
        assertEquals(CrewRole.PILOT, qualification.getRequiredRole());
        assertEquals("Airbus A320", qualification.getAircraftType().getModel());
        assertEquals(3, qualifications.findByAircraftTypeId(
                qualification.getAircraftType().getId()).size());
        assertEquals(qualification.getId(), qualifications.findByAircraftTypeIdAndRequiredRole(
                qualification.getAircraftType().getId(), CrewRole.PILOT).orElseThrow().getId());
        assertEquals(held.getId(), crewQualifications.findByCrewIdAndQualificationId(
                pilot.getId(), qualification.getId()).orElseThrow().getId());
        held.setValidUntil(null);
        entityManager.flush();
        entityManager.clear();
        assertNull(crewQualifications.findById(held.getId()).orElseThrow().getValidUntil());
    }

    @Test
    void flightQueriesUseHalfOpenDepartureRangeAndLazyForeignKeys() {
        List<Flight> range = flights.findDepartingInRange(instant("2026-12-01T05:00:00Z"),
                instant("2026-12-01T06:00:00Z"));
        assertEquals(1, range.size());
        Flight flight = range.getFirst();
        assertFalse(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(flight, "aircraft"));
        assertEquals("DEL", flight.getSourceAirport().getIataCode());
        assertEquals("BOM", flight.getDestinationAirport().getIataCode());
        assertEquals("VT-AR101", flight.getAircraft().getRegistration());
        assertEquals("Airbus A320", flight.getAircraft().getAircraftType().getModel());
        assertEquals(3, flights.findByAircraftIdOrderByDepartureTimeAsc(
                flight.getAircraft().getId()).size());
        assertEquals(5, flights.findByStatus(FlightStatus.SCHEDULED).size());
        assertTrue(flights.findByStatus(FlightStatus.CANCELLED).isEmpty());
        assertTrue(flights.findDepartingInRange(instant("2026-12-03T00:00:00Z"),
                instant("2026-12-04T00:00:00Z")).isEmpty());
    }

    @Test
    void assignmentQueriesExcludeCancelledRecordsAndLoadSnapshot() {
        Crew pilot = pilot();
        Flight flight = firstFlight();
        DutyAssignment assignment = assignments.findByCrewIdAndFlightIdAndStatus(
                pilot.getId(), flight.getId(), AssignmentStatus.ACTIVE).getFirst();
        assertEquals(1, assignments.findByCrewIdAndStatus(pilot.getId(), AssignmentStatus.ACTIVE).size());
        assertEquals(4, assignments.findByFlightIdAndStatus(flight.getId(), AssignmentStatus.ACTIVE).size());
        assertEquals(CrewRole.PILOT, assignment.getAssignedRole());
        assertEquals(pilot.getId(), assignment.getCrew().getId());
        assertEquals(flight.getId(), assignment.getFlight().getId());
        assertEquals(instant("2026-12-01T04:00:00Z"), assignment.getDutyStartTime());
        assertEquals(instant("2026-12-01T07:40:00Z"), assignment.getDutyEndTime());
        assertNotNull(assignment.getCreatedAt());
        assignment.setStatus(AssignmentStatus.CANCELLED);
        entityManager.flush();
        entityManager.clear();
        assertTrue(assignments.findByCrewIdAndFlightIdAndStatus(
                pilot.getId(), flight.getId(), AssignmentStatus.ACTIVE).isEmpty());
        assertEquals(AssignmentStatus.CANCELLED, assignments.findById(assignment.getId()).orElseThrow().getStatus());
        assertEquals(3, assignments.findByFlightIdAndStatus(flight.getId(), AssignmentStatus.ACTIVE).size());

        DutyAssignment previousCancellation = new DutyAssignment();
        previousCancellation.setCrew(crew.getReferenceById(pilot.getId()));
        previousCancellation.setFlight(flights.getReferenceById(flight.getId()));
        previousCancellation.setAssignedRole(CrewRole.PILOT);
        previousCancellation.setDutyStartTime(assignment.getDutyStartTime());
        previousCancellation.setDutyEndTime(assignment.getDutyEndTime());
        previousCancellation.setStatus(AssignmentStatus.CANCELLED);
        assignments.saveAndFlush(previousCancellation);
        assertEquals(2, assignments.findByCrewIdAndFlightIdAndStatus(
                pilot.getId(), flight.getId(), AssignmentStatus.CANCELLED).size());
    }

    @Test
    void unavailabilityOverlapExcludesTouchingEndpoints() {
        Crew tara = crew.findByLicenseNo("DEMO-F-004").orElseThrow();
        CrewUnavailability absence = unavailability.findByCrewIdOrderByStartTimeAsc(tara.getId()).getFirst();
        assertEquals(tara.getId(), absence.getCrew().getId());
        assertEquals("Training", absence.getReason());
        assertEquals(1, unavailability.findOverlapping(tara.getId(),
                instant("2026-12-01T09:59:59Z"), instant("2026-12-01T11:00:00Z")).size());
        assertTrue(unavailability.findOverlapping(tara.getId(),
                instant("2026-12-01T10:00:00Z"), instant("2026-12-01T11:00:00Z")).isEmpty());
        assertTrue(unavailability.findOverlapping(tara.getId(),
                instant("2026-12-01T03:00:00Z"), instant("2026-12-01T04:00:00Z")).isEmpty());
        assertTrue(unavailability.findOverlapping(pilot().getId(), absence.getStartTime(), absence.getEndTime()).isEmpty());
    }

    @Test
    void identityEnumsUtcMicrosecondsAndDatabaseAuditRoundTrip() {
        Flight flight = new Flight();
        flight.setFlightNumber("TEST-UTC");
        flight.setSourceAirport(airports.findByIataCode("DEL").orElseThrow());
        flight.setDestinationAirport(airports.findByIataCode("BOM").orElseThrow());
        flight.setAircraft(aircraft.findByRegistration("VT-AR101").orElseThrow());
        Instant departure = instant("2026-12-05T23:30:00.123456+05:30");
        flight.setDepartureTime(departure);
        flight.setArrivalTime(departure.plusSeconds(7200));
        assertEquals(FlightStatus.SCHEDULED, flight.getStatus());
        flights.saveAndFlush(flight);
        Long id = flight.getId();
        assertNotNull(id);
        entityManager.clear();
        Flight loaded = flights.findById(id).orElseThrow();
        assertEquals(departure, loaded.getDepartureTime());
        assertEquals(departure.plusSeconds(7200), loaded.getArrivalTime());
        for (FlightStatus status : List.of(FlightStatus.COMPLETED, FlightStatus.CANCELLED)) {
            loaded.setStatus(status);
            entityManager.flush();
            entityManager.clear();
            loaded = flights.findById(id).orElseThrow();
            assertEquals(status, loaded.getStatus());
        }
        flights.delete(loaded);
        flights.flush();
        List<FlightAudit> history = audits.findByFlightIdOrderByActionTimeAscIdAsc(id);
        assertEquals(List.of("INSERT", "UPDATE", "UPDATE", "DELETE"),
                history.stream().map(FlightAudit::getAction).toList());
        assertTrue(history.stream().allMatch(a -> a.getActionTime() != null && a.getFlightId().equals(id)));
        assertEquals("TEST-UTC", history.getFirst().getFlightNumber());
        assertTrue(audits.findById(history.getFirst().getId()).isPresent());
        assertEquals(9, audits.findAll(PageRequest.of(0, 20)).getTotalElements());
    }

    @Test
    void newAssignmentUsesIdentityAndDatabaseCreationTimestamp() {
        DutyAssignment assignment = new DutyAssignment();
        assignment.setCrew(pilot());
        Flight flight = flights.findDepartingInRange(instant("2026-12-02T00:00:00Z"),
                instant("2026-12-03T00:00:00Z")).getFirst();
        assignment.setFlight(flight);
        assignment.setAssignedRole(CrewRole.PILOT);
        assignment.setDutyStartTime(flight.getDepartureTime().minusSeconds(3600));
        assignment.setDutyEndTime(flight.getArrivalTime().plusSeconds(1800));
        assignments.saveAndFlush(assignment);
        assertNotNull(assignment.getId());
        assertNotNull(assignment.getCreatedAt());
        assertEquals(AssignmentStatus.ACTIVE, assignment.getStatus());
    }

    @Test
    void newEntityGraphPersistsReferenceTablesAndAllCrewRoles() {
        Airport airport = new Airport();
        airport.setIataCode("TST");
        airport.setName("Test Airport");
        airport.setCity("Test City");
        airport.setCountry("Test Country");
        airports.saveAndFlush(airport);

        AircraftType type = new AircraftType();
        type.setModel("Test Model");
        type.setManufacturer("Test Manufacturer");
        type.setCapacity(100);
        type.setMinCabinCrew(2);
        aircraftTypes.saveAndFlush(type);

        Aircraft plane = new Aircraft();
        plane.setRegistration("TEST-PLANE");
        plane.setAircraftType(type);
        aircraft.saveAndFlush(plane);
        assertTrue(plane.isActive());

        for (CrewRole role : CrewRole.values()) {
            Crew member = new Crew();
            member.setName("Test " + role);
            member.setRole(role);
            member.setLicenseNo("TEST-" + role);
            member.setExperienceYears(2);
            member.setContactNo("0000000000");
            crew.saveAndFlush(member);
            assertTrue(member.isActive());

            Qualification qualification = new Qualification();
            qualification.setAircraftType(type);
            qualification.setRequiredRole(role);
            qualifications.saveAndFlush(qualification);

            CrewQualification held = new CrewQualification();
            held.setCrew(member);
            held.setQualification(qualification);
            held.setValidUntil(LocalDate.of(2028, 1, 1));
            crewQualifications.saveAndFlush(held);

            CrewUnavailability absence = new CrewUnavailability();
            absence.setCrew(member);
            absence.setStartTime(instant("2026-12-10T00:00:00.123456Z"));
            absence.setEndTime(instant("2026-12-10T01:00:00.123456Z"));
            absence.setReason("Test training");
            unavailability.saveAndFlush(absence);
        }
        entityManager.clear();
        assertEquals("Test Country", airports.findById(airport.getId()).orElseThrow().getCountry());
        assertEquals(type.getId(), aircraft.findById(plane.getId()).orElseThrow().getAircraftType().getId());
        for (CrewRole role : CrewRole.values()) {
            Crew member = crew.findByLicenseNo("TEST-" + role).orElseThrow();
            assertEquals(role, member.getRole());
            CrewQualification held = crewQualifications.findByCrewId(member.getId()).getFirst();
            assertEquals(role, held.getQualification().getRequiredRole());
            assertEquals(LocalDate.of(2028, 1, 1), held.getValidUntil());
            CrewUnavailability absence = unavailability.findByCrewIdOrderByStartTimeAsc(member.getId()).getFirst();
            assertEquals(instant("2026-12-10T00:00:00.123456Z"), absence.getStartTime());
            assertEquals(instant("2026-12-10T01:00:00.123456Z"), absence.getEndTime());
        }
    }

    @Test
    void databaseRejectsNonexistentForeignKey() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into aircraft (registration, aircraft_type_id) values (?, ?)",
                "TEST-INVALID-FK", -1L));
    }
}
