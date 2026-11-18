package com.aeroroster.service;

import com.aeroroster.persistence.entity.Aircraft;
import com.aeroroster.persistence.entity.AssignmentStatus;
import com.aeroroster.persistence.entity.Airport;
import com.aeroroster.persistence.entity.Flight;
import com.aeroroster.persistence.entity.FlightStatus;
import com.aeroroster.persistence.repository.AircraftRepository;
import com.aeroroster.persistence.repository.AirportRepository;
import com.aeroroster.persistence.repository.DutyAssignmentRepository;
import com.aeroroster.persistence.repository.FlightRepository;
import com.aeroroster.service.dto.FlightFilter;
import com.aeroroster.service.dto.FlightRequest;
import com.aeroroster.service.dto.FlightView;
import com.aeroroster.service.exception.BusinessConflictException;
import com.aeroroster.service.exception.DuplicateResourceException;
import com.aeroroster.service.exception.InvalidInputException;
import com.aeroroster.service.exception.ResourceNotFoundException;
import com.aeroroster.service.validation.InputValues;
import com.aeroroster.service.validation.ServiceValidation;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class FlightService {
    private final FlightRepository flights;
    private final AircraftRepository aircraft;
    private final AirportRepository airports;
    private final DutyAssignmentRepository assignments;
    private final ServiceValidation validation;
    private final ServiceTransactions transactions;
    private final ServiceLocks locks;
    private final Clock clock;

    public FlightService(FlightRepository flights, AircraftRepository aircraft, AirportRepository airports,
            DutyAssignmentRepository assignments, ServiceValidation validation,
            ServiceTransactions transactions, ServiceLocks locks, Clock clock) {
        this.flights = flights;
        this.aircraft = aircraft;
        this.airports = airports;
        this.assignments = assignments;
        this.validation = validation;
        this.transactions = transactions;
        this.locks = locks;
        this.clock = clock;
    }

    public FlightView create(FlightRequest request) {
        validate(request);
        return transactions.write(() -> {
            Aircraft plane = locks.aircraft(request.aircraftId());
            checkAvailable(plane, request, null);
            Flight flight = new Flight();
            apply(flight, request, plane);
            return ServiceViews.flight(flights.saveAndFlush(flight));
        });
    }

    public FlightView get(Long id) {
        validation.id(id);
        return transactions.read(() -> ServiceViews.flight(flights.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight was not found"))));
    }

    public Page<FlightView> search(FlightFilter filter, Pageable pageable) {
        validation.request(filter);
        validation.pageable(pageable);
        if (filter.departureFrom() != null && filter.departureUntil() != null) {
            validation.window(filter.departureFrom(), filter.departureUntil());
        }
        return transactions.read(() -> flights.search(InputValues.searchPattern(filter.flightNumber()),
                filter.status(), filter.aircraftId(), filter.departureFrom(), filter.departureUntil(), pageable)
                .map(ServiceViews::flight));
    }

    public List<FlightView> forAircraft(Long aircraftId) {
        validation.id(aircraftId);
        return transactions.read(() -> {
            if (!aircraft.existsById(aircraftId)) {
                throw new ResourceNotFoundException("Aircraft was not found");
            }
            return flights.findByAircraftIdOrderByDepartureTimeAsc(aircraftId).stream().map(ServiceViews::flight).toList();
        });
    }

    public FlightView update(Long id, FlightRequest request) {
        return update(id, request, null);
    }

    public FlightView update(Long id, FlightRequest request, ExpectedVersion expected) {
        validation.id(id);
        validate(request);
        return transactions.write(() -> {
            Flight flight = locks.flight(id);
            ExpectedVersion.check(expected, flight.getId(), flight.getVersion());
            requireScheduled(flight);
            // Lock both the old and requested aircraft, always in ascending ID order.
            List<Aircraft> locked = Stream.of(flight.getAircraft().getId(), request.aircraftId())
                    .distinct().sorted().map(locks::aircraft).toList();
            Aircraft plane = locked.stream().filter(value -> value.getId().equals(request.aircraftId()))
                    .findFirst().orElseThrow();
            if (changed(flight, request) && assignments.existsByFlightIdAndStatus(id, AssignmentStatus.ACTIVE)) {
                throw new BusinessConflictException("Flight has active duty assignments. Resolve them before changing flight details");
            }
            checkAvailable(plane, request, id);
            apply(flight, request, plane);
            flights.flush();
            return ServiceViews.flight(flight);
        });
    }

    public FlightView cancel(Long id) {
        return changeStatus(id, FlightStatus.CANCELLED);
    }

    public FlightView cancel(Long id, ExpectedVersion expected) {
        return changeStatus(id, FlightStatus.CANCELLED, expected);
    }

    public FlightView changeStatus(Long id, FlightStatus status) {
        return changeStatus(id, status, null);
    }

    public FlightView changeStatus(Long id, FlightStatus status, ExpectedVersion expected) {
        validation.id(id);
        if (status == null) {
            throw new InvalidInputException("Flight status is required");
        }
        return transactions.write(() -> {
            Flight flight = locks.flight(id);
            ExpectedVersion.check(expected, flight.getId(), flight.getVersion());
            locks.aircraft(flight.getAircraft().getId());
            if (flight.getStatus() == status) {
                return ServiceViews.flight(flight);
            }
            requireScheduled(flight);
            if (status == FlightStatus.CANCELLED && assignments.existsByFlightIdAndStatus(id, AssignmentStatus.ACTIVE)) {
                throw new BusinessConflictException("Cancel active duty assignments before cancelling the flight");
            }
            if (status == FlightStatus.COMPLETED && flight.getArrivalTime().isAfter(clock.instant())) {
                throw new BusinessConflictException("Flight cannot be completed before its scheduled arrival");
            }
            flight.setStatus(status);
            flights.flush();
            return ServiceViews.flight(flight);
        });
    }

    private void validate(FlightRequest request) {
        validation.request(request);
        validation.window(request.departureTime(), request.arrivalTime());
        if (request.sourceAirportId().equals(request.destinationAirportId())) {
            throw new InvalidInputException("Source and destination airports must differ");
        }
    }

    private void checkAvailable(Aircraft plane, FlightRequest request, Long excludedId) {
        if (!plane.isActive()) {
            throw new BusinessConflictException("Aircraft must be active for flight creation or rescheduling");
        }
        Long duplicateExcludedId = excludedId == null ? -1L : excludedId;
        if (flights.existsByFlightNumberIgnoreCaseAndDepartureTimeAndIdNot(
                request.flightNumber(), request.departureTime(), duplicateExcludedId)) {
            throw new DuplicateResourceException("Flight number and departure time already identify an existing flight");
        }
        if (flights.hasAircraftOverlap(plane.getId(), request.departureTime(), request.arrivalTime(), excludedId)) {
            throw new BusinessConflictException("Aircraft is already scheduled during this interval");
        }
    }

    private Airport airport(Long id) {
        return airports.findById(id).orElseThrow(() -> new ResourceNotFoundException("Airport was not found"));
    }

    private void requireScheduled(Flight flight) {
        if (flight.getStatus() != FlightStatus.SCHEDULED) {
            throw new BusinessConflictException("Completed and cancelled flights are terminal and cannot be changed");
        }
    }

    private boolean changed(Flight flight, FlightRequest request) {
        return !Objects.equals(flight.getFlightNumber(), request.flightNumber())
                || !flight.getSourceAirport().getId().equals(request.sourceAirportId())
                || !flight.getDestinationAirport().getId().equals(request.destinationAirportId())
                || !flight.getAircraft().getId().equals(request.aircraftId())
                || !flight.getDepartureTime().equals(request.departureTime())
                || !flight.getArrivalTime().equals(request.arrivalTime());
    }

    private void apply(Flight flight, FlightRequest request, Aircraft plane) {
        Airport source = airport(request.sourceAirportId());
        Airport destination = airport(request.destinationAirportId());
        flight.setFlightNumber(request.flightNumber());
        flight.setSourceAirport(source);
        flight.setDestinationAirport(destination);
        flight.setAircraft(plane);
        flight.setDepartureTime(request.departureTime());
        flight.setArrivalTime(request.arrivalTime());
    }

    public Page<FlightView> forAircraft(Long aircraftId, Pageable pageable) {
        validation.id(aircraftId);
        validation.pageable(pageable);
        return transactions.read(() -> {
            if (!aircraft.existsById(aircraftId)) {
                throw new ResourceNotFoundException("Aircraft was not found");
            }
            return flights.findByAircraftIdOrderByDepartureTimeAscIdAsc(aircraftId, pageable).map(ServiceViews::flight);
        });
    }
}
