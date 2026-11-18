package com.aeroroster.service;

import com.aeroroster.domain.DutyWindow;
import com.aeroroster.domain.scheduling.*;
import com.aeroroster.persistence.entity.AssignmentStatus;
import com.aeroroster.persistence.entity.Crew;
import com.aeroroster.persistence.entity.CrewRole;
import com.aeroroster.persistence.entity.Flight;
import com.aeroroster.persistence.entity.FlightStatus;
import com.aeroroster.persistence.repository.*;
import com.aeroroster.service.dto.AssignmentRequest;
import com.aeroroster.service.dto.EligibilityDecision;
import com.aeroroster.service.validation.ServiceValidation;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

import static com.aeroroster.domain.scheduling.SchedulingReasonCode.*;

@Service
public class CrewEligibilityService {
    private final CrewRepository crew;
    private final FlightRepository flights;
    private final CrewQualificationRepository qualifications;
    private final DutyAssignmentRepository assignments;
    private final CrewUnavailabilityRepository unavailability;
    private final SchedulingPolicy policy;
    private final DutyLoadCalculator calculator;
    private final DutyRules dutyRules;
    private final ServiceTransactions transactions;
    private final ServiceValidation validation;

    public CrewEligibilityService(CrewRepository crew, FlightRepository flights,
            CrewQualificationRepository qualifications, DutyAssignmentRepository assignments,
            CrewUnavailabilityRepository unavailability, SchedulingPolicy policy, DutyLoadCalculator calculator,
            DutyRules dutyRules, ServiceTransactions transactions, ServiceValidation validation) {
        this.crew = crew;
        this.flights = flights;
        this.qualifications = qualifications;
        this.assignments = assignments;
        this.unavailability = unavailability;
        this.policy = policy;
        this.calculator = calculator;
        this.dutyRules = dutyRules;
        this.transactions = transactions;
        this.validation = validation;
    }

    /** Advisory preview. Assignment writes always evaluate again under parent row locks. */
    public EligibilityDecision check(AssignmentRequest request) {
        validation.request(request);
        return transactions.read(() -> evaluate(request, crew.findById(request.crewId()).orElse(null),
                flights.findById(request.flightId()).orElse(null)));
    }

    EligibilityDecision evaluate(AssignmentRequest request, Crew member, Flight flight) {
        List<SchedulingReason> reasons = new ArrayList<>();
        if (member == null) {
            reasons.add(reason(CREW_NOT_FOUND, "Crew member was not found"));
        } else {
            if (!member.isActive()) {
                reasons.add(reason(CREW_INACTIVE, "Crew member must be active"));
            }
            if (member.getRole() != request.assignedRole()) {
                reasons.add(reason(ROLE_MISMATCH, "Requested assignment role must match the crew member's role"));
            }
        }
        if (flight == null) {
            reasons.add(reason(FLIGHT_NOT_FOUND, "Flight was not found"));
            return decision(null, null, reasons);
        }
        if (flight.getStatus() != FlightStatus.SCHEDULED) {
            reasons.add(reason(FLIGHT_NOT_SCHEDULED, "Only scheduled flights can receive assignments"));
        }
        if (!flight.getAircraft().isActive()) {
            reasons.add(reason(AIRCRAFT_INACTIVE, "Aircraft must be active"));
        }
        if (request.assignedRole() != CrewRole.CABIN_CREW && assignments.countByFlightIdAndAssignedRoleAndStatus(
                flight.getId(), request.assignedRole(), AssignmentStatus.ACTIVE) > 0) {
            reasons.add(reason(FLIGHT_ROLE_FILLED, "Flight already has an active " + request.assignedRole() + " assignment"));
        }
        if (member != null) {
            checkQualification(request, flight, reasons);
            if (assignments.existsByCrewIdAndFlightIdAndStatus(member.getId(), flight.getId(), AssignmentStatus.ACTIVE)) {
                reasons.add(reason(DUPLICATE_ACTIVE_ASSIGNMENT, "Crew already has an active assignment on this flight"));
            }
        }

        DutyWindow window;
        Instant from;
        Instant until;
        try {
            window = policy.dutyWindow(flight.getDepartureTime(), flight.getArrivalTime());
            Duration margin = policy.minimumRest().compareTo(Duration.ofDays(7)) > 0
                    ? policy.minimumRest() : Duration.ofDays(7);
            from = window.start().minus(margin);
            until = window.end().plus(margin);
        } catch (DateTimeException | ArithmeticException | IllegalArgumentException exception) {
            reasons.add(reason(INVALID_DUTY_WINDOW, "A valid duty window enclosing the flight could not be calculated"));
            return decision(null, null, reasons);
        }
        DutyLoad load = null;
        if (member != null) {
            List<DutyWindow> active = assignments.findActiveWindows(member.getId(), from, until);
            List<DutyWindow> unavailable = unavailability.findWindows(member.getId(), window.start(), window.end(), null);
            load = calculator.assess(window, active);
            reasons.addAll(dutyRules.evaluate(window, active, unavailable, load, policy));
        }
        return decision(window, load, reasons);
    }

    private void checkQualification(AssignmentRequest request, Flight flight, List<SchedulingReason> reasons) {
        List<QualificationValidity> held = qualifications.findValidity(request.crewId(),
                flight.getAircraft().getAircraftType().getId(), request.assignedRole());
        if (held.isEmpty()) {
            reasons.add(reason(MISSING_QUALIFICATION, "Crew lacks the aircraft-type qualification for the requested role"));
        } else {
            LocalDate departureDate = flight.getDepartureTime().atZone(ZoneOffset.UTC).toLocalDate();
            boolean valid = held.stream().anyMatch(value -> value.validUntil() == null
                    || !value.validUntil().isBefore(departureDate));
            if (!valid) {
                reasons.add(reason(QUALIFICATION_EXPIRED, "Qualification expires before the flight's UTC departure date"));
            }
        }
    }

    private EligibilityDecision decision(DutyWindow window, DutyLoad load, List<SchedulingReason> reasons) {
        return new EligibilityDecision(reasons.isEmpty(), window, load, reasons, policy, SchedulingPolicy.NOTICE);
    }

    private SchedulingReason reason(SchedulingReasonCode code, String explanation) {
        return new SchedulingReason(code, explanation);
    }
}
