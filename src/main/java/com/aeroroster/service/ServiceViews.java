package com.aeroroster.service;

import com.aeroroster.persistence.entity.*;
import com.aeroroster.service.dto.*;

/** Maps inside service transactions, so every returned record is independent of JPA. */
final class ServiceViews {
    private ServiceViews() {
    }

    static CrewView crew(Crew crew) {
        return new CrewView(crew.getId(), crew.getName(), crew.getRole(), crew.getLicenseNo(),
                crew.getExperienceYears(), crew.getContactNo(), crew.isActive());
    }

    static AircraftView aircraft(Aircraft aircraft) {
        return new AircraftView(aircraft.getId(), aircraft.getRegistration(),
                aircraft.getAircraftType().getId(), aircraft.getAircraftType().getModel(), aircraft.isActive());
    }

    static AircraftTypeView aircraftType(AircraftType type) {
        return new AircraftTypeView(type.getId(), type.getModel(), type.getManufacturer(),
                type.getCapacity(), type.getMinCabinCrew());
    }

    static AirportView airport(Airport airport) {
        return new AirportView(airport.getId(), airport.getIataCode(), airport.getName(), airport.getCity(), airport.getCountry());
    }

    static QualificationView qualification(Qualification qualification) {
        return new QualificationView(qualification.getId(), qualification.getAircraftType().getId(),
                qualification.getAircraftType().getModel(), qualification.getRequiredRole());
    }

    static CrewQualificationView crewQualification(CrewQualification held) {
        Qualification qualification = held.getQualification();
        return new CrewQualificationView(held.getId(), held.getCrew().getId(), qualification.getId(),
                qualification.getAircraftType().getId(), qualification.getAircraftType().getModel(),
                qualification.getRequiredRole(), held.getValidUntil());
    }

    static FlightView flight(Flight flight) {
        return new FlightView(flight.getId(), flight.getFlightNumber(),
                flight.getSourceAirport().getId(), flight.getSourceAirport().getIataCode(),
                flight.getDestinationAirport().getId(), flight.getDestinationAirport().getIataCode(),
                flight.getAircraft().getId(), flight.getAircraft().getRegistration(),
                flight.getAircraft().getAircraftType().getId(), flight.getDepartureTime(),
                flight.getArrivalTime(), flight.getStatus());
    }

    static AssignmentView assignment(DutyAssignment assignment) {
        return new AssignmentView(assignment.getId(), assignment.getCrew().getId(),
                assignment.getFlight().getId(), assignment.getFlight().getFlightNumber(),
                assignment.getAssignedRole(), assignment.getDutyStartTime(), assignment.getDutyEndTime(),
                assignment.getStatus(), assignment.getCreatedAt());
    }
}
