package com.aeroroster.service;

import com.aeroroster.persistence.entity.Aircraft;
import com.aeroroster.persistence.entity.AircraftType;
import com.aeroroster.persistence.entity.FlightStatus;
import com.aeroroster.persistence.repository.AircraftRepository;
import com.aeroroster.persistence.repository.AircraftTypeRepository;
import com.aeroroster.persistence.repository.FlightRepository;
import com.aeroroster.service.dto.AircraftFilter;
import com.aeroroster.service.dto.AircraftRequest;
import com.aeroroster.service.dto.AircraftView;
import com.aeroroster.service.exception.BusinessConflictException;
import com.aeroroster.service.exception.DuplicateResourceException;
import com.aeroroster.service.exception.ResourceNotFoundException;
import com.aeroroster.service.validation.InputValues;
import com.aeroroster.service.validation.ServiceValidation;
import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class AircraftService {
    private final AircraftRepository aircraft;
    private final AircraftTypeRepository types;
    private final FlightRepository flights;
    private final ServiceValidation validation;
    private final ServiceTransactions transactions;
    private final ServiceLocks locks;
    private final Clock clock;

    public AircraftService(AircraftRepository aircraft, AircraftTypeRepository types, FlightRepository flights,
            ServiceValidation validation, ServiceTransactions transactions, ServiceLocks locks, Clock clock) {
        this.aircraft = aircraft;
        this.types = types;
        this.flights = flights;
        this.validation = validation;
        this.transactions = transactions;
        this.locks = locks;
        this.clock = clock;
    }

    public AircraftView register(AircraftRequest request) {
        validation.request(request);
        return transactions.write(() -> {
            checkRegistration(request.registration(), -1L);
            Aircraft plane = new Aircraft();
            plane.setRegistration(request.registration());
            plane.setAircraftType(type(request.aircraftTypeId()));
            return ServiceViews.aircraft(aircraft.saveAndFlush(plane));
        });
    }

    public AircraftView get(Long id) {
        validation.id(id);
        return transactions.read(() -> ServiceViews.aircraft(aircraft.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aircraft was not found"))));
    }

    public Page<AircraftView> search(AircraftFilter filter, Pageable pageable) {
        validation.request(filter);
        validation.pageable(pageable);
        return transactions.read(() -> aircraft.search(InputValues.searchPattern(filter.query()),
                filter.active(), filter.aircraftTypeId(), pageable).map(ServiceViews::aircraft));
    }

    public AircraftView update(Long id, AircraftRequest request) {
        return update(id, request, null);
    }

    public AircraftView update(Long id, AircraftRequest request, ExpectedVersion expected) {
        validation.id(id);
        validation.request(request);
        return transactions.write(() -> {
            Aircraft plane = locks.aircraft(id);
            ExpectedVersion.check(expected, plane.getId(), plane.getVersion());
            AircraftType type = type(request.aircraftTypeId());
            if (!plane.getAircraftType().getId().equals(type.getId()) && flights.existsByAircraftId(id)) {
                throw new BusinessConflictException("Aircraft type cannot change after the aircraft has referenced flights");
            }
            checkRegistration(request.registration(), id);
            plane.setRegistration(request.registration());
            plane.setAircraftType(type);
            aircraft.flush();
            return ServiceViews.aircraft(plane);
        });
    }

    public AircraftView setActive(Long id, boolean active) {
        return setActive(id, active, null);
    }

    public AircraftView setActive(Long id, boolean active, ExpectedVersion expected) {
        validation.id(id);
        return transactions.write(() -> {
            Aircraft plane = locks.aircraft(id);
            ExpectedVersion.check(expected, plane.getId(), plane.getVersion());
            if (!active && flights.existsByAircraftIdAndStatusAndArrivalTimeAfter(id, FlightStatus.SCHEDULED, clock.instant())) {
                throw new BusinessConflictException("Aircraft has ongoing or future scheduled flights. Resolve them before deactivation");
            }
            plane.setActive(active);
            aircraft.flush();
            return ServiceViews.aircraft(plane);
        });
    }

    private AircraftType type(Long id) {
        return types.findById(id).orElseThrow(() -> new ResourceNotFoundException("Aircraft type was not found"));
    }

    private void checkRegistration(String registration, Long excludedId) {
        if (aircraft.existsByRegistrationIgnoreCaseAndIdNot(registration, excludedId)) {
            throw new DuplicateResourceException("Aircraft registration already exists");
        }
    }
}
