package com.aeroroster.service;

import com.aeroroster.persistence.entity.Aircraft;
import com.aeroroster.persistence.entity.Crew;
import com.aeroroster.persistence.entity.Flight;
import com.aeroroster.persistence.entity.DutyAssignment;
import com.aeroroster.persistence.entity.CrewUnavailability;
import com.aeroroster.persistence.entity.CrewQualification;
import com.aeroroster.persistence.repository.AircraftRepository;
import com.aeroroster.persistence.repository.CrewRepository;
import com.aeroroster.persistence.repository.FlightRepository;
import com.aeroroster.persistence.repository.DutyAssignmentRepository;
import com.aeroroster.persistence.repository.CrewUnavailabilityRepository;
import com.aeroroster.persistence.repository.CrewQualificationRepository;
import com.aeroroster.service.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Component;

/** Refresh after locking so an outer transaction's first-level cache cannot hide committed changes. */
@Component
class ServiceLocks {
    private final CrewRepository crew;
    private final AircraftRepository aircraft;
    private final FlightRepository flights;
    private final EntityManager entityManager;
    private final DutyAssignmentRepository assignments;
    private final CrewUnavailabilityRepository unavailability;
    private final CrewQualificationRepository qualifications;

    ServiceLocks(CrewRepository crew, AircraftRepository aircraft, FlightRepository flights, EntityManager entityManager,
            DutyAssignmentRepository assignments, CrewUnavailabilityRepository unavailability,
            CrewQualificationRepository qualifications) {
        this.crew = crew;
        this.aircraft = aircraft;
        this.flights = flights;
        this.entityManager = entityManager;
        this.assignments = assignments;
        this.unavailability = unavailability;
        this.qualifications = qualifications;
    }

    // Acquire the owning crew lock before locking a held qualification.
    CrewQualification qualification(Long crewId, Long qualificationId) {
        CrewQualification held = qualifications.findByCrewIdAndQualificationId(crewId, qualificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Crew qualification was not found"));
        entityManager.refresh(held, LockModeType.PESSIMISTIC_WRITE);
        return held;
    }

    // Acquire the owning crew, flight and aircraft locks before locking an assignment.
    DutyAssignment assignment(Long id) {
        DutyAssignment assignment = assignments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Duty assignment was not found"));
        entityManager.refresh(assignment, LockModeType.PESSIMISTIC_WRITE);
        return assignment;
    }

    // Acquire the owning crew lock before locking an unavailable period.
    CrewUnavailability unavailability(Long crewId, Long id) {
        CrewUnavailability period = unavailability.findByIdAndCrewId(id, crewId)
                .orElseThrow(() -> new ResourceNotFoundException("Crew unavailability was not found"));
        entityManager.refresh(period, LockModeType.PESSIMISTIC_WRITE);
        return period;
    }

    Crew crew(Long id) {
        Crew member = crew.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Crew member was not found"));
        entityManager.refresh(member, LockModeType.PESSIMISTIC_WRITE);
        return member;
    }

    Aircraft aircraft(Long id) {
        Aircraft plane = aircraft.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aircraft was not found"));
        entityManager.refresh(plane, LockModeType.PESSIMISTIC_WRITE);
        return plane;
    }

    Flight flight(Long id) {
        Flight flight = flights.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight was not found"));
        entityManager.refresh(flight, LockModeType.PESSIMISTIC_WRITE);
        return flight;
    }
}
