package com.aeroroster.service;

import com.aeroroster.domain.scheduling.SchedulingReason;
import com.aeroroster.domain.scheduling.SchedulingReasonCode;
import com.aeroroster.persistence.entity.*;
import com.aeroroster.persistence.repository.*;
import com.aeroroster.service.dto.*;
import com.aeroroster.service.exception.*;
import com.aeroroster.support.FixedClockConfiguration;
import com.aeroroster.support.PostgresTest;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static com.aeroroster.domain.scheduling.SchedulingReasonCode.*;
import static org.junit.jupiter.api.Assertions.*;

@PostgresTest
@Import(FixedClockConfiguration.class)
@Transactional
class SchedulingIntegrationTest {
    @Autowired DutyAssignmentService duties;
    @Autowired CrewEligibilityService eligibility;
    @Autowired CrewUnavailabilityService unavailable;
    @Autowired CrewService crewService;
    @Autowired FlightService flightService;
    @Autowired AircraftService aircraftService;
    @Autowired QualificationService qualificationService;
    @Autowired CrewRepository crew;
    @Autowired AircraftRepository aircraft;
    @Autowired AircraftTypeRepository types;
    @Autowired AirportRepository airports;
    @Autowired QualificationRepository requirements;
    @Autowired DutyAssignmentRepository assignments;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired ServiceTransactions transactions;

    private Long member(String license) {
        return crew.findByLicenseNo(license).orElseThrow().getId();
    }

    private Long plane(String registration) {
        return aircraft.findByRegistration(registration).orElseThrow().getId();
    }

    private Long requirement(CrewRole role) {
        Long type = types.findByModel("Airbus A320").orElseThrow().getId();
        return requirements.findByAircraftTypeIdAndRequiredRole(type, role).orElseThrow().getId();
    }

    private FlightView flight(String number, String registration, String start, String end) {
        return flightService.create(new FlightRequest(number, airports.findByIataCode("DEL").orElseThrow().getId(),
                airports.findByIataCode("BOM").orElseThrow().getId(), plane(registration), Instant.parse(start), Instant.parse(end)));
    }

    private FlightView flight() {
        return flight("T23-FLIGHT", "VT-AR101", "2026-12-10T05:00:00Z", "2026-12-10T07:00:00Z");
    }

    private AssignmentRequest request(Long flightId, String license, CrewRole role) {
        return new AssignmentRequest(member(license), flightId, role);
    }

    private List<SchedulingReasonCode> codes(EligibilityDecision decision) {
        return decision.reasons().stream().map(SchedulingReason::code).toList();
    }

    private void rejected(AssignmentRequest request, SchedulingReasonCode code) {
        assertTrue(codes(duties.check(request)).contains(code));
        AssignmentRejectedException exception = assertThrows(AssignmentRejectedException.class, () -> duties.assign(request));
        assertTrue(codes(exception.getDecision()).contains(code));
    }

    @ParameterizedTest
    @CsvSource({"DEMO-P-001,PILOT", "DEMO-C-001,COPILOT", "DEMO-F-001,CABIN_CREW"})
    void validAssignmentPersistsRoleAndDerivedWindow(String license, CrewRole role) {
        FlightView flight = flight();
        AssignmentRequest request = request(flight.id(), license, role);
        EligibilityDecision preview = duties.check(request);
        assertTrue(preview.eligible());
        assertTrue(preview.policyNotice().contains("academic"));
        AssignmentView assignment = duties.assign(request);
        entityManager.clear();
        assertEquals(assignment, duties.get(assignment.id()));
        assertEquals(role, assignment.assignedRole());
        assertEquals(Instant.parse("2026-12-10T04:00:00Z"), assignment.dutyStartTime());
        assertEquals(Instant.parse("2026-12-10T07:30:00Z"), assignment.dutyEndTime());
        assertEquals(AssignmentStatus.ACTIVE, assignment.status());
        assertNotNull(assignment.createdAt());
        assertTrue(duties.activeForCrew(request.crewId()).stream().anyMatch(value -> value.id().equals(assignment.id())));
        assertEquals(1, duties.forFlight(flight.id()).size());
    }

    @Test
    void previewAccumulatesIndependentProblemsAndWriteRejectsThem() {
        FlightView flight = flight();
        Long member = member("DEMO-P-002");
        crewService.setActive(member, false);
        unavailable.add(member, new UnavailabilityRequest(Instant.parse("2026-12-10T04:00:00Z"), Instant.parse("2026-12-10T08:00:00Z"), "Training"));
        AssignmentRequest request = new AssignmentRequest(member, flight.id(), CrewRole.COPILOT);
        List<SchedulingReasonCode> codes = codes(eligibility.check(request));
        assertTrue(codes.containsAll(List.of(CREW_INACTIVE, ROLE_MISMATCH, MISSING_QUALIFICATION, CREW_UNAVAILABLE)));
        assertThrows(AssignmentRejectedException.class, () -> duties.assign(request));
        assertTrue(duties.forFlight(flight.id()).isEmpty());
    }

    @Test
    void missingAircraftTypeQualificationIsRejected() {
        rejected(request(flight().id(), "DEMO-P-003", CrewRole.PILOT), MISSING_QUALIFICATION);
    }

    @Test
    void expiredQualificationIsRejected() {
        qualificationService.updateExpiry(member("DEMO-P-002"), requirement(CrewRole.PILOT), new QualificationExpiryRequest(LocalDate.of(2026, 12, 9)));
        rejected(request(flight().id(), "DEMO-P-002", CrewRole.PILOT), QUALIFICATION_EXPIRED);
    }

    @Test
    void expiryIsInclusiveOnUtcDepartureDateForCrossMidnightDuty() {
        qualificationService.updateExpiry(member("DEMO-P-002"), requirement(CrewRole.PILOT), new QualificationExpiryRequest(LocalDate.of(2026, 12, 10)));
        FlightView flight = flight("T23-MIDNIGHT", "VT-AR101", "2026-12-10T23:00:00Z", "2026-12-11T01:00:00Z");
        AssignmentRequest request = request(flight.id(), "DEMO-P-002", CrewRole.PILOT);
        EligibilityDecision decision = duties.check(request);
        assertTrue(decision.eligible());
        assertEquals(Duration.ofHours(2), decision.dutyLoad().maximumUtcDay());
        AssignmentView assignment = duties.assign(request);
        assertEquals(Instant.parse("2026-12-11T01:30:00Z"), assignment.dutyEndTime());
    }

    @Test
    void openEndedQualificationIsValid() {
        qualificationService.updateExpiry(member("DEMO-P-002"), requirement(CrewRole.PILOT), new QualificationExpiryRequest(null));
        assertNotNull(duties.assign(request(flight().id(), "DEMO-P-002", CrewRole.PILOT)).id());
    }

    @Test
    void duplicateActiveAssignmentAndMultipleRolesAreRejected() {
        FlightView flight = flight();
        AssignmentRequest request = request(flight.id(), "DEMO-P-001", CrewRole.PILOT);
        duties.assign(request);
        rejected(request, DUPLICATE_ACTIVE_ASSIGNMENT);
        AssignmentRequest wrongRole = request(flight.id(), "DEMO-P-001", CrewRole.COPILOT);
        assertTrue(codes(duties.check(wrongRole)).containsAll(List.of(ROLE_MISMATCH, DUPLICATE_ACTIVE_ASSIGNMENT)));
        assertEquals(1, duties.forFlight(flight.id()).size());
    }

    @ParameterizedTest
    @CsvSource({"DEMO-P-001,DEMO-P-002,PILOT", "DEMO-C-001,DEMO-C-002,COPILOT"})
    void uniqueFlightRoleCannotBeOverfilled(String first, String second, CrewRole role) {
        Long flight = flight().id();
        duties.assign(request(flight, first, role));
        rejected(request(flight, second, role), FLIGHT_ROLE_FILLED);
        assertEquals(1, assignments.countByFlightIdAndAssignedRoleAndStatus(flight, role, AssignmentStatus.ACTIVE));
    }

    @Test
    void crewCannotHoldOverlappingDutiesOnDifferentAircraft() {
        FlightView first = flight();
        FlightView second = flight("T23-OVERLAP", "VT-AR102", "2026-12-10T06:00:00Z", "2026-12-10T08:00:00Z");
        duties.assign(request(first.id(), "DEMO-P-001", CrewRole.PILOT));
        rejected(request(second.id(), "DEMO-P-001", CrewRole.PILOT), CREW_TIME_CONFLICT);
        assertTrue(duties.forFlight(second.id()).isEmpty());
    }

    @Test
    void adjacentDutiesHaveNoOverlapButStillRequireRest() {
        FlightView first = flight();
        FlightView next = flight("T23-ADJACENT", "VT-AR101", "2026-12-10T08:30:00Z", "2026-12-10T10:30:00Z");
        duties.assign(request(first.id(), "DEMO-P-001", CrewRole.PILOT));
        AssignmentRequest request = request(next.id(), "DEMO-P-001", CrewRole.PILOT);
        assertFalse(codes(duties.check(request)).contains(CREW_TIME_CONFLICT));
        rejected(request, INSUFFICIENT_REST);
    }

    @Test
    void exactTenHourRestIsAllowedEvenWhenLaterDutyWasAssignedFirst() {
        FlightView first = flight();
        FlightView later = flight("T23-REST", "VT-AR101", "2026-12-10T18:30:00Z", "2026-12-10T20:30:00Z");
        duties.assign(request(later.id(), "DEMO-P-001", CrewRole.PILOT));
        assertNotNull(duties.assign(request(first.id(), "DEMO-P-001", CrewRole.PILOT)).id());
    }

    @Test
    void rolling24HourLimitRejectsDespiteSufficientRest() {
        FlightView first = flight("T23-LOAD-A", "VT-AR101", "2026-12-10T01:00:00Z", "2026-12-10T05:30:00Z");
        FlightView second = flight("T23-LOAD-B", "VT-AR101", "2026-12-10T17:00:00Z", "2026-12-10T21:30:00Z");
        duties.assign(request(first.id(), "DEMO-P-001", CrewRole.PILOT));
        AssignmentRequest request = request(second.id(), "DEMO-P-001", CrewRole.PILOT);
        assertEquals(Duration.ofHours(12), duties.check(request).dutyLoad().maximumRolling24Hours());
        assertFalse(codes(duties.check(request)).contains(INSUFFICIENT_REST));
        rejected(request, ROLLING_24_HOUR_LIMIT);
        assertTrue(duties.forFlight(second.id()).isEmpty());
    }

    @Test
    void rollingSevenDayLimitRejectsSixthEightHourDuty() {
        Instant start = Instant.parse("2026-12-10T01:00:00Z");
        for (int day = 0; day < 5; day++) {
            FlightView flight = flight("T23-WEEK-" + day, "VT-AR101", start.plus(Duration.ofDays(day)).toString(),
                    start.plus(Duration.ofDays(day)).plus(Duration.ofMinutes(390)).toString());
            duties.assign(request(flight.id(), "DEMO-P-001", CrewRole.PILOT));
        }
        FlightView sixth = flight("T23-WEEK-5", "VT-AR101", start.plus(Duration.ofDays(5)).toString(), start.plus(Duration.ofDays(5)).plus(Duration.ofMinutes(390)).toString());
        AssignmentRequest request = request(sixth.id(), "DEMO-P-001", CrewRole.PILOT);
        assertEquals(Duration.ofHours(48), duties.check(request).dutyLoad().maximumRolling7Days());
        rejected(request, ROLLING_7_DAY_LIMIT);
        assertTrue(duties.forFlight(sixth.id()).isEmpty());
    }

    @Test
    void multiDateDutyReportsAllExceededLimits() {
        FlightView flight = flight("T23-MULTIDAY", "VT-AR101", "2026-12-10T01:00:00Z", "2026-12-12T02:00:00Z");
        AssignmentRequest request = request(flight.id(), "DEMO-P-001", CrewRole.PILOT);
        assertTrue(codes(duties.check(request)).containsAll(List.of(ROLLING_24_HOUR_LIMIT, ROLLING_7_DAY_LIMIT, UTC_DAILY_DUTY_LIMIT)));
        assertThrows(AssignmentRejectedException.class, () -> duties.assign(request));
        assertTrue(duties.forFlight(flight.id()).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = FlightStatus.class, names = {"CANCELLED", "COMPLETED"})
    void terminalFlightsRejectAssignments(FlightStatus status) {
        FlightView flight = flight("T23-TERMINAL", "VT-AR101", "2026-11-17T05:00:00Z", "2026-11-17T07:00:00Z");
        flightService.changeStatus(flight.id(), status);
        rejected(request(flight.id(), "DEMO-P-001", CrewRole.PILOT), FLIGHT_NOT_SCHEDULED);
    }

    @Test
    void inactiveAircraftRejectsAssignments() {
        Long type = types.findByModel("Airbus A320").orElseThrow().getId();
        AircraftView plane = aircraftService.register(new AircraftRequest("T23-INACTIVE", type));
        FlightView flight = flight("T23-INACTIVE", plane.registration(), "2026-11-17T05:00:00Z", "2026-11-17T07:00:00Z");
        aircraftService.setActive(plane.id(), false);
        rejected(request(flight.id(), "DEMO-P-001", CrewRole.PILOT), AIRCRAFT_INACTIVE);
    }

    @Test
    void staffingCanBeBuiltIncrementallyAndAllowsExtraCabinCrew() {
        Long flight = flight().id();
        FlightCrewCoverage empty = duties.coverage(flight);
        assertFalse(empty.minimumStaffingSatisfied());
        assertEquals(List.of(1, 1, 2), empty.roles().stream().map(RoleCoverage::missing).toList());
        duties.assign(request(flight, "DEMO-P-001", CrewRole.PILOT));
        assertFalse(duties.coverage(flight).minimumStaffingSatisfied());
        duties.assign(request(flight, "DEMO-C-001", CrewRole.COPILOT));
        duties.assign(request(flight, "DEMO-F-001", CrewRole.CABIN_CREW));
        duties.assign(request(flight, "DEMO-F-002", CrewRole.CABIN_CREW));
        assertTrue(duties.coverage(flight).minimumStaffingSatisfied());
        assertTrue(duties.coverage(flight).reasons().isEmpty());
        duties.assign(request(flight, "DEMO-F-003", CrewRole.CABIN_CREW));
        assertEquals(3, duties.coverage(flight).roles().get(2).active());
    }

    @Test
    void cancellationAndReassignmentPreserveHistoryAndRecheckEligibility() {
        AssignmentRequest request = request(flight().id(), "DEMO-P-001", CrewRole.PILOT);
        AssignmentView original = duties.assign(request);
        assertThrows(BusinessConflictException.class, () -> duties.reassign(original.id()));
        assertEquals(AssignmentStatus.CANCELLED, duties.cancel(original.id()).status());
        assertEquals(AssignmentStatus.CANCELLED, duties.cancel(original.id()).status());
        AssignmentView replacement = duties.reassign(original.id());
        assertNotEquals(original.id(), replacement.id());
        assertEquals(AssignmentStatus.CANCELLED, duties.get(original.id()).status());
        assertEquals(2, duties.historyForFlight(request.flightId()).size());
        assertEquals(1, duties.forFlight(request.flightId()).size());
        assertTrue(duties.historyForCrew(request.crewId()).stream().anyMatch(value -> value.id().equals(original.id())));
        assertFalse(duties.activeForCrew(request.crewId()).stream().anyMatch(value -> value.id().equals(original.id())));
        assertThrows(AssignmentRejectedException.class, () -> duties.reassign(original.id()));
    }

    @Test
    void unavailabilityBlocksAssignmentsAndChangesPreserveDutyIntegrity() {
        Long member = member("DEMO-P-002");
        UnavailabilityView period = unavailable.add(member, new UnavailabilityRequest(Instant.parse("2026-12-10T04:00:00Z"), Instant.parse("2026-12-10T08:00:00Z"), " Training "));
        assertEquals("Training", period.reason());
        AssignmentRequest request = request(flight().id(), "DEMO-P-002", CrewRole.PILOT);
        rejected(request, CREW_UNAVAILABLE);
        unavailable.remove(member, period.id());
        AssignmentView assignment = duties.assign(request);
        assertThrows(BusinessConflictException.class, () -> unavailable.add(member, new UnavailabilityRequest(period.startTime(), period.endTime(), "Training")));
        assertTrue(unavailable.list(member).isEmpty());
        UnavailabilityView later = unavailable.add(member, new UnavailabilityRequest(Instant.parse("2026-12-10T18:00:00Z"), Instant.parse("2026-12-10T19:00:00Z"), "Meeting"));
        assertThrows(BusinessConflictException.class, () -> unavailable.update(member, later.id(), new UnavailabilityRequest(period.startTime(), period.endTime(), "Moved meeting")));
        assertEquals(later, unavailable.list(member).getFirst());
        duties.cancel(assignment.id());
        unavailable.add(member, new UnavailabilityRequest(period.startTime(), period.endTime(), "Training"));
        assertThrows(AssignmentRejectedException.class, () -> duties.reassign(assignment.id()));
        assertTrue(duties.forFlight(request.flightId()).isEmpty());
    }

    @Test
    void unavailablePeriodsSupportSafeEditsAndHalfOpenBoundaries() {
        Long member = member("DEMO-P-002");
        Instant start = Instant.parse("2026-12-10T04:00:00Z");
        Instant end = Instant.parse("2026-12-10T08:00:00Z");
        UnavailabilityView period = unavailable.add(member, new UnavailabilityRequest(start, end, "Training"));
        assertEquals(period.id(), unavailable.update(member, period.id(), new UnavailabilityRequest(start, end, "Updated")).id());
        assertTrue(unavailable.conflicts(member, start.plusSeconds(1), end));
        assertFalse(unavailable.conflicts(member, end, end.plusSeconds(1)));
        unavailable.add(member, new UnavailabilityRequest(end, end.plusSeconds(3600), "Adjacent"));
        assertThrows(BusinessConflictException.class, () -> unavailable.add(member, new UnavailabilityRequest(start.plusSeconds(1), end.plusSeconds(1), "Overlapping")));
        assertThrows(ResourceNotFoundException.class, () -> unavailable.remove(member("DEMO-P-001"), period.id()));
        assertEquals(2, unavailable.list(member).size());
    }

    @Test
    void invalidRequestsAndMissingReferencesHaveTypedResults() {
        Long missing = Long.MAX_VALUE;
        EligibilityDecision result = duties.check(new AssignmentRequest(missing, missing, CrewRole.PILOT));
        assertEquals(List.of(CREW_NOT_FOUND, FLIGHT_NOT_FOUND), codes(result));
        assertThrows(InvalidInputException.class, () -> duties.assign(new AssignmentRequest(null, null, null)));
        assertThrows(ResourceNotFoundException.class, () -> duties.get(missing));
        assertThrows(ResourceNotFoundException.class, () -> duties.cancel(missing));
        assertThrows(ResourceNotFoundException.class, () -> duties.reassign(missing));
        assertThrows(ResourceNotFoundException.class, () -> duties.forFlight(missing));
        assertThrows(ResourceNotFoundException.class, () -> duties.activeForCrew(missing));
        assertThrows(ResourceNotFoundException.class, () -> duties.coverage(missing));
        assertThrows(ResourceNotFoundException.class, () -> unavailable.list(missing));
        assertThrows(InvalidInputException.class, () -> unavailable.add(member("DEMO-P-002"), new UnavailabilityRequest(Instant.EPOCH, Instant.EPOCH, " ")));
    }

    @Test
    void actualAssignmentIgnoresEarlierEligiblePreviewAndProtectsPhase22Operations() {
        FlightView flight = flight();
        AssignmentRequest request = request(flight.id(), "DEMO-P-002", CrewRole.PILOT);
        assertTrue(duties.check(request).eligible());
        crewService.setActive(request.crewId(), false);
        assertThrows(AssignmentRejectedException.class, () -> duties.assign(request));
        crewService.setActive(request.crewId(), true);
        AssignmentView assignment = duties.assign(request);
        assertThrows(BusinessConflictException.class, () -> crewService.setActive(request.crewId(), false));
        assertThrows(BusinessConflictException.class, () -> qualificationService.revoke(request.crewId(), requirement(CrewRole.PILOT)));
        assertThrows(BusinessConflictException.class, () -> qualificationService.updateExpiry(request.crewId(), requirement(CrewRole.PILOT), new QualificationExpiryRequest(LocalDate.of(2026, 12, 9))));
        assertThrows(BusinessConflictException.class, () -> flightService.cancel(flight.id()));
        assertThrows(BusinessConflictException.class, () -> flightService.update(flight.id(), new FlightRequest(flight.flightNumber(), flight.sourceAirportId(), flight.destinationAirportId(), flight.aircraftId(), flight.departureTime().plusSeconds(60), flight.arrivalTime().plusSeconds(60))));
        duties.cancel(assignment.id());
        assertFalse(crewService.setActive(request.crewId(), false).active());
        assertEquals(FlightStatus.CANCELLED, flightService.cancel(flight.id()).status());
    }

    @Test
    void databasePartialUniqueIndexStillRejectsDuplicateActivePair() {
        AssignmentView assignment = duties.assign(request(flight().id(), "DEMO-P-002", CrewRole.PILOT));
        assertThrows(DuplicateResourceException.class, () -> transactions.write(() -> jdbc.update("""
                insert into duty_assignment (crew_id, flight_id, assigned_role, duty_start_time, duty_end_time)
                select crew_id, flight_id, assigned_role, duty_start_time, duty_end_time from duty_assignment where id = ?
                """, assignment.id())));
    }
}
