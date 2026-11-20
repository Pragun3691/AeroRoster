package com.aeroroster.service;

import com.aeroroster.domain.scheduling.SchedulingReason;
import com.aeroroster.domain.scheduling.SchedulingReasonCode;
import com.aeroroster.persistence.entity.AssignmentStatus;
import com.aeroroster.persistence.entity.CrewRole;
import com.aeroroster.persistence.entity.FlightStatus;
import com.aeroroster.persistence.repository.AircraftRepository;
import com.aeroroster.persistence.repository.AircraftTypeRepository;
import com.aeroroster.persistence.repository.AirportRepository;
import com.aeroroster.persistence.repository.CrewRepository;
import com.aeroroster.persistence.repository.DutyAssignmentRepository;
import com.aeroroster.service.dto.AircraftTypeView;
import com.aeroroster.service.dto.AssignmentRequest;
import com.aeroroster.service.dto.AssignmentView;
import com.aeroroster.service.dto.CrewQualificationView;
import com.aeroroster.service.dto.EligibilityDecision;
import com.aeroroster.service.dto.FlightCrewCoverage;
import com.aeroroster.service.dto.FlightRequest;
import com.aeroroster.service.dto.FlightView;
import com.aeroroster.service.dto.QualificationExpiryRequest;
import com.aeroroster.service.dto.RoleCoverage;
import com.aeroroster.service.dto.UnavailabilityRequest;
import com.aeroroster.service.exception.AssignmentRejectedException;
import com.aeroroster.service.exception.StaleVersionException;
import com.aeroroster.support.FixedClockConfiguration;
import com.aeroroster.support.PostgresTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static com.aeroroster.domain.scheduling.SchedulingReasonCode.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the end-to-end flight creation and guided crew assignment workflow:
 * Create empty flight -> Verify requirements -> Candidate eligibility checks ->
 * Incremental assignments (Pilot, Co-pilot, Cabin Crew) -> Live coverage updates ->
 * Invalid assignment rejections -> Cancellation and history retention.
 */
@PostgresTest
@Import(FixedClockConfiguration.class)
@Transactional
class FlightCrewAssignmentWorkflowTest {

    @Autowired DutyAssignmentService duties;
    @Autowired CrewEligibilityService eligibility;
    @Autowired CrewUnavailabilityService unavailable;
    @Autowired CrewService crewService;
    @Autowired FlightService flightService;
    @Autowired AircraftService aircraftService;
    @Autowired AircraftTypeService aircraftTypeService;
    @Autowired QualificationService qualificationService;

    @Autowired CrewRepository crew;
    @Autowired AircraftRepository aircraft;
    @Autowired AircraftTypeRepository types;
    @Autowired AirportRepository airports;
    @Autowired DutyAssignmentRepository assignments;
    @Autowired EntityManager entityManager;

    private Long member(String license) {
        return crew.findByLicenseNo(license).orElseThrow().getId();
    }

    private Long plane(String registration) {
        return aircraft.findByRegistration(registration).orElseThrow().getId();
    }

    private FlightView createEmptyFlight(String flightNumber, String registration, String dep, String arr) {
        Long origin = airports.findByIataCode("DEL").orElseThrow().getId();
        Long destination = airports.findByIataCode("BOM").orElseThrow().getId();
        return flightService.create(new FlightRequest(
                flightNumber,
                origin,
                destination,
                plane(registration),
                Instant.parse(dep),
                Instant.parse(arr)
        ));
    }

    private List<SchedulingReasonCode> codes(EligibilityDecision decision) {
        return decision.reasons().stream().map(SchedulingReason::code).toList();
    }

    @Test
    @DisplayName("1. Create flight without crew assignments: persisted in PostgreSQL with zero assignments and incomplete coverage")
    void test1_createFlightWithoutCrewAssignments() {
        FlightView flight = createEmptyFlight("WF-101", "VT-AR101", "2026-12-15T06:00:00Z", "2026-12-15T08:30:00Z");
        assertNotNull(flight.id());
        assertEquals("WF-101", flight.flightNumber());
        assertEquals(FlightStatus.SCHEDULED, flight.status());

        // Assert 0 active assignments exist
        List<AssignmentView> active = duties.forFlight(flight.id());
        assertTrue(active.isEmpty(), "Newly created flight must have zero crew assignments");

        // Assert initial coverage is incomplete with 0/4 crew
        FlightCrewCoverage coverage = duties.coverage(flight.id());
        assertFalse(coverage.minimumStaffingSatisfied());
        assertEquals(3, coverage.roles().size());

        RoleCoverage pilotRole = coverage.roles().stream().filter(r -> r.role() == CrewRole.PILOT).findFirst().orElseThrow();
        assertEquals(1, pilotRole.required());
        assertEquals(0, pilotRole.active());
        assertEquals(1, pilotRole.missing());

        RoleCoverage copilotRole = coverage.roles().stream().filter(r -> r.role() == CrewRole.COPILOT).findFirst().orElseThrow();
        assertEquals(1, copilotRole.required());
        assertEquals(0, copilotRole.active());
        assertEquals(1, copilotRole.missing());

        RoleCoverage cabinRole = coverage.roles().stream().filter(r -> r.role() == CrewRole.CABIN_CREW).findFirst().orElseThrow();
        assertEquals(2, cabinRole.required());
        assertEquals(0, cabinRole.active());
        assertEquals(2, cabinRole.missing());

        List<SchedulingReasonCode> reasonCodes = coverage.reasons().stream().map(SchedulingReason::code).toList();
        assertTrue(reasonCodes.containsAll(List.of(MISSING_PILOT, MISSING_COPILOT, MISSING_CABIN_CREW)));
    }

    @Test
    @DisplayName("2. Retrieve aircraft-specific crew requirements from database")
    void test2_retrieveAircraftSpecificCrewRequirements() {
        List<AircraftTypeView> allTypes = aircraftTypeService.list();
        assertFalse(allTypes.isEmpty());

        AircraftTypeView a320 = allTypes.stream().filter(t -> t.model().equals("Airbus A320")).findFirst().orElseThrow();
        assertEquals(180, a320.capacity());
        assertEquals(2, a320.minCabinCrew());

        AircraftTypeView b738 = allTypes.stream().filter(t -> t.model().equals("Boeing 737-800")).findFirst().orElseThrow();
        assertEquals(189, b738.capacity());
        assertEquals(2, b738.minCabinCrew());
    }

    @Test
    @DisplayName("3. Retrieve eligible crew candidates for flight and validate candidate preview")
    void test3_retrieveEligibleCrewCandidatesForFlight() {
        FlightView flight = createEmptyFlight("WF-102", "VT-AR101", "2026-12-15T09:00:00Z", "2026-12-15T11:00:00Z");

        // Ravi Sharma (DEMO-P-001) is qualified on A320 as PILOT
        AssignmentRequest pilotReq = new AssignmentRequest(member("DEMO-P-001"), flight.id(), CrewRole.PILOT);
        EligibilityDecision pilotDecision = duties.check(pilotReq);
        assertTrue(pilotDecision.eligible());
        assertNotNull(pilotDecision.dutyWindow());
        assertTrue(pilotDecision.reasons().isEmpty());

        // Kiran Mehta (DEMO-P-003) is qualified ONLY on B738, not on A320 -> must be ineligible
        AssignmentRequest b738PilotReq = new AssignmentRequest(member("DEMO-P-003"), flight.id(), CrewRole.PILOT);
        EligibilityDecision b738Decision = duties.check(b738PilotReq);
        assertFalse(b738Decision.eligible());
        assertTrue(codes(b738Decision).contains(MISSING_QUALIFICATION));
    }

    @Test
    @DisplayName("4. Step-by-step complete workflow: 0/4 -> 1/4 -> 2/4 -> 3/4 -> 4/4 (MINIMUM STAFFING SATISFIED)")
    void test4_completeIncrementalWorkflowToSatisfiedCoverage() {
        FlightView flight = createEmptyFlight("WF-FULL", "VT-AR101", "2026-12-16T04:00:00Z", "2026-12-16T06:30:00Z");

        // Initial: 0/4
        FlightCrewCoverage cov0 = duties.coverage(flight.id());
        assertFalse(cov0.minimumStaffingSatisfied());
        assertEquals(0, duties.forFlight(flight.id()).size());

        // Step 1: Assign Pilot (DEMO-P-001) -> 1/4
        AssignmentView pilotAssignment = duties.assign(new AssignmentRequest(member("DEMO-P-001"), flight.id(), CrewRole.PILOT));
        assertNotNull(pilotAssignment.id());
        assertEquals(CrewRole.PILOT, pilotAssignment.assignedRole());

        FlightCrewCoverage cov1 = duties.coverage(flight.id());
        assertFalse(cov1.minimumStaffingSatisfied());
        assertEquals(1, cov1.roles().stream().filter(r -> r.role() == CrewRole.PILOT).findFirst().orElseThrow().active());
        assertEquals(0, cov1.roles().stream().filter(r -> r.role() == CrewRole.PILOT).findFirst().orElseThrow().missing());

        // Step 2: Assign Co-Pilot (DEMO-C-001) -> 2/4
        AssignmentView copilotAssignment = duties.assign(new AssignmentRequest(member("DEMO-C-001"), flight.id(), CrewRole.COPILOT));
        assertNotNull(copilotAssignment.id());
        assertEquals(CrewRole.COPILOT, copilotAssignment.assignedRole());

        FlightCrewCoverage cov2 = duties.coverage(flight.id());
        assertFalse(cov2.minimumStaffingSatisfied());
        assertEquals(1, cov2.roles().stream().filter(r -> r.role() == CrewRole.COPILOT).findFirst().orElseThrow().active());

        // Step 3: Assign Cabin Crew 1 (DEMO-F-001) -> 3/4
        AssignmentView cc1 = duties.assign(new AssignmentRequest(member("DEMO-F-001"), flight.id(), CrewRole.CABIN_CREW));
        assertNotNull(cc1.id());

        FlightCrewCoverage cov3 = duties.coverage(flight.id());
        assertFalse(cov3.minimumStaffingSatisfied());
        assertEquals(1, cov3.roles().stream().filter(r -> r.role() == CrewRole.CABIN_CREW).findFirst().orElseThrow().active());
        assertEquals(1, cov3.roles().stream().filter(r -> r.role() == CrewRole.CABIN_CREW).findFirst().orElseThrow().missing());

        // Step 4: Assign Cabin Crew 2 (DEMO-F-002) -> 4/4 (MINIMUM STAFFING SATISFIED)
        AssignmentView cc2 = duties.assign(new AssignmentRequest(member("DEMO-F-002"), flight.id(), CrewRole.CABIN_CREW));
        assertNotNull(cc2.id());

        FlightCrewCoverage cov4 = duties.coverage(flight.id());
        assertTrue(cov4.minimumStaffingSatisfied(), "With 1 pilot, 1 copilot, and 2 cabin crew, minimum staffing MUST be satisfied");
        assertTrue(cov4.reasons().isEmpty(), "No scheduling deficit reasons should remain when minimum staffing is satisfied");
        assertEquals(4, duties.forFlight(flight.id()).size());
    }

    @Test
    @DisplayName("7. Reject incorrect crew roles (assigning pilot as copilot)")
    void test7_rejectIncorrectCrewRoles() {
        FlightView flight = createEmptyFlight("WF-ROLE", "VT-AR101", "2026-12-17T05:00:00Z", "2026-12-17T07:00:00Z");
        // DEMO-P-001 is a PILOT, attempting to assign as COPILOT
        AssignmentRequest badRole = new AssignmentRequest(member("DEMO-P-001"), flight.id(), CrewRole.COPILOT);
        EligibilityDecision dec = duties.check(badRole);
        assertFalse(dec.eligible());
        assertTrue(codes(dec).contains(ROLE_MISMATCH));
        assertThrows(AssignmentRejectedException.class, () -> duties.assign(badRole));
    }

    @Test
    @DisplayName("8. Reject inactive crew members")
    void test8_rejectInactiveCrew() {
        FlightView flight = createEmptyFlight("WF-INACT", "VT-AR101", "2026-12-17T08:00:00Z", "2026-12-17T10:00:00Z");
        Long pilotId = member("DEMO-P-002");
        crewService.setActive(pilotId, false);

        AssignmentRequest req = new AssignmentRequest(pilotId, flight.id(), CrewRole.PILOT);
        EligibilityDecision dec = duties.check(req);
        assertFalse(dec.eligible());
        assertTrue(codes(dec).contains(CREW_INACTIVE));
        assertThrows(AssignmentRejectedException.class, () -> duties.assign(req));
    }

    @Test
    @DisplayName("9. Reject expired qualifications")
    void test9_rejectExpiredQualifications() {
        FlightView flight = createEmptyFlight("WF-EXP", "VT-AR101", "2026-12-18T05:00:00Z", "2026-12-18T07:00:00Z");
        Long pilotId = member("DEMO-P-002");
        CrewQualificationView heldQual = qualificationService.forCrew(pilotId).stream()
                .filter(q -> q.model().equals("Airbus A320") && q.requiredRole() == CrewRole.PILOT)
                .findFirst().orElseThrow();

        // Set expiry date before flight departure
        qualificationService.updateExpiry(pilotId, heldQual.qualificationId(),
                new QualificationExpiryRequest(LocalDate.parse("2026-12-01")));

        AssignmentRequest req = new AssignmentRequest(pilotId, flight.id(), CrewRole.PILOT);
        EligibilityDecision dec = duties.check(req);
        assertFalse(dec.eligible());
        assertTrue(codes(dec).contains(QUALIFICATION_EXPIRED));
        assertThrows(AssignmentRejectedException.class, () -> duties.assign(req));
    }

    @Test
    @DisplayName("10. Reject crew with unavailability during duty window")
    void test10_rejectUnavailableCrew() {
        FlightView flight = createEmptyFlight("WF-UNAVAIL", "VT-AR101", "2026-12-18T10:00:00Z", "2026-12-18T12:00:00Z");
        Long pilotId = member("DEMO-P-001");
        unavailable.add(pilotId, new UnavailabilityRequest(
                Instant.parse("2026-12-18T08:00:00Z"),
                Instant.parse("2026-12-18T14:00:00Z"),
                "Medical checkup"
        ));

        AssignmentRequest req = new AssignmentRequest(pilotId, flight.id(), CrewRole.PILOT);
        EligibilityDecision dec = duties.check(req);
        assertFalse(dec.eligible());
        assertTrue(codes(dec).contains(CREW_UNAVAILABLE));
        assertThrows(AssignmentRejectedException.class, () -> duties.assign(req));
    }

    @Test
    @DisplayName("11. Reject overlapping assignments")
    void test11_rejectOverlappingAssignments() {
        FlightView flight1 = createEmptyFlight("WF-OV1", "VT-AR101", "2026-12-19T06:00:00Z", "2026-12-19T08:00:00Z");
        FlightView flight2 = createEmptyFlight("WF-OV2", "VT-AR102", "2026-12-19T07:00:00Z", "2026-12-19T09:00:00Z");

        duties.assign(new AssignmentRequest(member("DEMO-P-001"), flight1.id(), CrewRole.PILOT));

        // Overlapping flight2 assignment for same pilot must be rejected with CREW_TIME_CONFLICT
        AssignmentRequest conflictReq = new AssignmentRequest(member("DEMO-P-001"), flight2.id(), CrewRole.PILOT);
        EligibilityDecision dec = duties.check(conflictReq);
        assertFalse(dec.eligible());
        assertTrue(codes(dec).contains(CREW_TIME_CONFLICT));
        assertThrows(AssignmentRejectedException.class, () -> duties.assign(conflictReq));
    }

    @Test
    @DisplayName("12. Reject duplicate assignments on same flight")
    void test12_rejectDuplicateAssignments() {
        FlightView flight = createEmptyFlight("WF-DUP", "VT-AR101", "2026-12-20T05:00:00Z", "2026-12-20T07:00:00Z");
        duties.assign(new AssignmentRequest(member("DEMO-F-001"), flight.id(), CrewRole.CABIN_CREW));

        // Attempting to assign DEMO-F-001 again on the same flight
        AssignmentRequest dupReq = new AssignmentRequest(member("DEMO-F-001"), flight.id(), CrewRole.CABIN_CREW);
        EligibilityDecision dec = duties.check(dupReq);
        assertFalse(dec.eligible());
        assertTrue(codes(dec).contains(DUPLICATE_ACTIVE_ASSIGNMENT));
        assertThrows(AssignmentRejectedException.class, () -> duties.assign(dupReq));
    }

    @Test
    @DisplayName("13. Reject invalid aircraft or cancelled flight combinations")
    void test13_rejectInvalidAircraftFlightCombinations() {
        FlightView flight = createEmptyFlight("WF-CX", "VT-AR101", "2026-12-20T10:00:00Z", "2026-12-20T12:00:00Z");
        flightService.cancel(flight.id());

        AssignmentRequest req = new AssignmentRequest(member("DEMO-P-001"), flight.id(), CrewRole.PILOT);
        EligibilityDecision dec = duties.check(req);
        assertFalse(dec.eligible());
        assertTrue(codes(dec).contains(FLIGHT_NOT_SCHEDULED));
        assertThrows(AssignmentRejectedException.class, () -> duties.assign(req));
    }

    @Test
    @DisplayName("15. Verify cancellation updates active coverage and preserves history")
    void test15_verifyCancellationUpdatesCoverageAndPreservesHistory() {
        FlightView flight = createEmptyFlight("WF-CANCEL", "VT-AR101", "2026-12-21T05:00:00Z", "2026-12-21T07:00:00Z");

        AssignmentView pilot = duties.assign(new AssignmentRequest(member("DEMO-P-001"), flight.id(), CrewRole.PILOT));
        AssignmentView copilot = duties.assign(new AssignmentRequest(member("DEMO-C-001"), flight.id(), CrewRole.COPILOT));
        AssignmentView cc1 = duties.assign(new AssignmentRequest(member("DEMO-F-001"), flight.id(), CrewRole.CABIN_CREW));
        AssignmentView cc2 = duties.assign(new AssignmentRequest(member("DEMO-F-002"), flight.id(), CrewRole.CABIN_CREW));

        // Coverage is satisfied: 4/4
        assertTrue(duties.coverage(flight.id()).minimumStaffingSatisfied());

        // Cancel the pilot's assignment
        AssignmentView cancelled = duties.cancel(pilot.id());
        assertEquals(AssignmentStatus.CANCELLED, cancelled.status());

        // Coverage must drop back to incomplete: 3/4
        FlightCrewCoverage updatedCov = duties.coverage(flight.id());
        assertFalse(updatedCov.minimumStaffingSatisfied());
        RoleCoverage pilotRole = updatedCov.roles().stream().filter(r -> r.role() == CrewRole.PILOT).findFirst().orElseThrow();
        assertEquals(0, pilotRole.active());
        assertEquals(1, pilotRole.missing());
        assertTrue(updatedCov.reasons().stream().anyMatch(r -> r.code() == MISSING_PILOT));

        // Historical record is preserved in database
        List<AssignmentView> history = duties.historyForFlight(flight.id());
        assertEquals(4, history.size());
        assertTrue(history.stream().anyMatch(a -> a.id().equals(pilot.id()) && a.status() == AssignmentStatus.CANCELLED));
    }

    @Test
    @DisplayName("16. Preserve concurrency protection: stale expected version is rejected")
    void test16_preserveConcurrencyProtection() {
        FlightView flight = createEmptyFlight("WF-CONC", "VT-AR101", "2026-12-22T05:00:00Z", "2026-12-22T07:00:00Z");
        AssignmentView pilot = duties.assign(new AssignmentRequest(member("DEMO-P-001"), flight.id(), CrewRole.PILOT));

        // Providing a stale expected version
        ExpectedVersion wrongVersion = new ExpectedVersion(pilot.id(), pilot.version() + 99);
        assertThrows(StaleVersionException.class, () -> duties.cancel(pilot.id(), wrongVersion));

        // Verifying assignment is still active
        assertEquals(AssignmentStatus.ACTIVE, duties.get(pilot.id()).status());
    }
}
