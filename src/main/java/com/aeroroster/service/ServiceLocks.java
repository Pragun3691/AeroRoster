package com.aeroroster.service;

import com.aeroroster.persistence.entity.Aircraft;
import com.aeroroster.persistence.entity.Crew;
import com.aeroroster.persistence.entity.Flight;
import com.aeroroster.persistence.repository.AircraftRepository;
import com.aeroroster.persistence.repository.CrewRepository;
import com.aeroroster.persistence.repository.FlightRepository;
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

    ServiceLocks(CrewRepository crew, AircraftRepository aircraft, FlightRepository flights, EntityManager entityManager) {
        this.crew = crew;
        this.aircraft = aircraft;
        this.flights = flights;
        this.entityManager = entityManager;
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
