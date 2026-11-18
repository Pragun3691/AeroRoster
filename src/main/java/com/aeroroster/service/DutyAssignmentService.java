package com.aeroroster.service;

import com.aeroroster.domain.scheduling.AssignmentIdentity;
import com.aeroroster.domain.scheduling.SchedulingPolicy;
import com.aeroroster.domain.scheduling.SchedulingReason;
import com.aeroroster.domain.scheduling.SchedulingReasonCode;
import com.aeroroster.persistence.entity.*;
import com.aeroroster.persistence.repository.CrewRepository;
import com.aeroroster.persistence.repository.DutyAssignmentRepository;
import com.aeroroster.persistence.repository.FlightRepository;
import com.aeroroster.service.dto.*;
import com.aeroroster.service.exception.AssignmentRejectedException;
import com.aeroroster.service.exception.BusinessConflictException;
import com.aeroroster.service.exception.ResourceNotFoundException;
import com.aeroroster.service.validation.ServiceValidation;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class DutyAssignmentService {
    private final DutyAssignmentRepository assignments;
    private final CrewRepository crew;
    private final FlightRepository flights;
    private final CrewEligibilityService eligibility;
    private final ServiceLocks locks;
    private final ServiceTransactions transactions;
    private final ServiceValidation validation;

    public DutyAssignmentService(DutyAssignmentRepository assignments, CrewRepository crew, FlightRepository flights,
            CrewEligibilityService eligibility, ServiceLocks locks, ServiceTransactions transactions,
            ServiceValidation validation) {
        this.assignments = assignments;
        this.crew = crew;
        this.flights = flights;
        this.eligibility = eligibility;
        this.locks = locks;
        this.transactions = transactions;
        this.validation = validation;
    }

    public EligibilityDecision check(AssignmentRequest request) {
        return eligibility.check(request);
    }

    public AssignmentView assign(AssignmentRequest request) {
        validation.request(request);
        return transactions.write(() -> {
            Crew member = locks.crew(request.crewId());
            Flight flight = lockFlightAndAircraft(request.flightId());
            return create(request, member, flight);
        });
    }

    public AssignmentView get(Long id) {
        validation.id(id);
        return transactions.read(() -> ServiceViews.assignment(assignments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Duty assignment was not found"))));
    }

    public List<AssignmentView> forFlight(Long flightId) {
        validation.id(flightId);
        return transactions.read(() -> {
            requireFlight(flightId);
            return assignments.findByFlightIdAndStatus(flightId, AssignmentStatus.ACTIVE)
                    .stream().map(ServiceViews::assignment).toList();
        });
    }

    public List<AssignmentView> activeForCrew(Long crewId) {
        validation.id(crewId);
        return transactions.read(() -> {
            requireCrew(crewId);
            return assignments.findByCrewIdAndStatus(crewId, AssignmentStatus.ACTIVE)
                    .stream().map(ServiceViews::assignment).toList();
        });
    }

    public List<AssignmentView> historyForCrew(Long crewId) {
        validation.id(crewId);
        return transactions.read(() -> {
            requireCrew(crewId);
            return assignments.findByCrewIdOrderByDutyStartTimeAscIdAsc(crewId)
                    .stream().map(ServiceViews::assignment).toList();
        });
    }

    public List<AssignmentView> historyForFlight(Long flightId) {
        validation.id(flightId);
        return transactions.read(() -> {
            requireFlight(flightId);
            return assignments.findByFlightIdOrderByDutyStartTimeAscIdAsc(flightId)
                    .stream().map(ServiceViews::assignment).toList();
        });
    }

    public AssignmentView cancel(Long id) {
        validation.id(id);
        return transactions.write(() -> {
            AssignmentIdentity identity = identity(id);
            locks.crew(identity.crewId());
            lockFlightAndAircraft(identity.flightId());
            DutyAssignment assignment = locks.assignment(id);
            assignment.setStatus(AssignmentStatus.CANCELLED);
            assignments.flush();
            return ServiceViews.assignment(assignment);
        });
    }

    public AssignmentView reassign(Long cancelledAssignmentId) {
        validation.id(cancelledAssignmentId);
        return transactions.write(() -> {
            AssignmentIdentity identity = identity(cancelledAssignmentId);
            Crew member = locks.crew(identity.crewId());
            Flight flight = lockFlightAndAircraft(identity.flightId());
            DutyAssignment previous = locks.assignment(cancelledAssignmentId);
            if (previous.getStatus() != AssignmentStatus.CANCELLED) {
                throw new BusinessConflictException("Cancel the previous assignment before reassigning");
            }
            return create(new AssignmentRequest(member.getId(), flight.getId(), previous.getAssignedRole()), member, flight);
        });
    }

    public FlightCrewCoverage coverage(Long flightId) {
        validation.id(flightId);
        return transactions.read(() -> {
            Flight flight = flights.findById(flightId)
                    .orElseThrow(() -> new ResourceNotFoundException("Flight was not found"));
            List<CrewRole> active = assignments.findActiveRoles(flightId);
            List<RoleCoverage> roles = new ArrayList<>();
            List<SchedulingReason> reasons = new ArrayList<>();
            for (CrewRole role : CrewRole.values()) {
                int required = role == CrewRole.CABIN_CREW ? flight.getAircraft().getAircraftType().getMinCabinCrew() : 1;
                int count = (int) active.stream().filter(value -> value == role).count();
                int missing = Math.max(0, required - count);
                roles.add(new RoleCoverage(role, required, count, missing));
                if (missing > 0) {
                    SchedulingReasonCode code = switch (role) {
                        case PILOT -> SchedulingReasonCode.MISSING_PILOT;
                        case COPILOT -> SchedulingReasonCode.MISSING_COPILOT;
                        case CABIN_CREW -> SchedulingReasonCode.MISSING_CABIN_CREW;
                    };
                    reasons.add(new SchedulingReason(code, "Flight needs " + missing + " additional " + role + " assignment(s)"));
                }
                if (role != CrewRole.CABIN_CREW && count > 1) {
                    SchedulingReasonCode code = role == CrewRole.PILOT
                            ? SchedulingReasonCode.EXCESS_PILOTS : SchedulingReasonCode.EXCESS_COPILOTS;
                    reasons.add(new SchedulingReason(code, "Flight has multiple active " + role + " assignments"));
                }
            }
            return new FlightCrewCoverage(flightId, flight.getStatus(), roles, reasons.isEmpty(), reasons, SchedulingPolicy.NOTICE);
        });
    }

    private AssignmentView create(AssignmentRequest request, Crew member, Flight flight) {
        EligibilityDecision decision = eligibility.evaluate(request, member, flight);
        if (!decision.eligible()) {
            throw new AssignmentRejectedException(decision);
        }
        DutyAssignment assignment = new DutyAssignment();
        assignment.setCrew(member);
        assignment.setFlight(flight);
        assignment.setAssignedRole(request.assignedRole());
        assignment.setDutyStartTime(decision.dutyWindow().start());
        assignment.setDutyEndTime(decision.dutyWindow().end());
        return ServiceViews.assignment(assignments.saveAndFlush(assignment));
    }

    private Flight lockFlightAndAircraft(Long id) {
        Flight flight = locks.flight(id);
        locks.aircraft(flight.getAircraft().getId());
        return flight;
    }

    private AssignmentIdentity identity(Long id) {
        return assignments.findIdentity(id).orElseThrow(() -> new ResourceNotFoundException("Duty assignment was not found"));
    }

    private void requireCrew(Long id) {
        if (!crew.existsById(id)) {
            throw new ResourceNotFoundException("Crew member was not found");
        }
    }

    private void requireFlight(Long id) {
        if (!flights.existsById(id)) {
            throw new ResourceNotFoundException("Flight was not found");
        }
    }

    public Page<AssignmentView> forFlight(Long flightId, Pageable pageable) {
        validation.id(flightId);
        validation.pageable(pageable);
        return transactions.read(() -> {
            requireFlight(flightId);
            return assignments.findByFlightIdAndStatusOrderByDutyStartTimeAscIdAsc(flightId, AssignmentStatus.ACTIVE, pageable).map(ServiceViews::assignment);
        });
    }

    public Page<AssignmentView> activeForCrew(Long crewId, Pageable pageable) {
        validation.id(crewId);
        validation.pageable(pageable);
        return transactions.read(() -> {
            requireCrew(crewId);
            return assignments.findByCrewIdAndStatusOrderByDutyStartTimeAscIdAsc(crewId, AssignmentStatus.ACTIVE, pageable).map(ServiceViews::assignment);
        });
    }

    public Page<AssignmentView> historyForCrew(Long crewId, Pageable pageable) {
        validation.id(crewId);
        validation.pageable(pageable);
        return transactions.read(() -> {
            requireCrew(crewId);
            return assignments.findByCrewIdOrderByDutyStartTimeAscIdAsc(crewId, pageable).map(ServiceViews::assignment);
        });
    }

    public Page<AssignmentView> historyForFlight(Long flightId, Pageable pageable) {
        validation.id(flightId);
        validation.pageable(pageable);
        return transactions.read(() -> {
            requireFlight(flightId);
            return assignments.findByFlightIdOrderByDutyStartTimeAscIdAsc(flightId, pageable).map(ServiceViews::assignment);
        });
    }
}
