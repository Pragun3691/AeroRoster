package com.aeroroster.service.dto;

import com.aeroroster.persistence.entity.FlightStatus;
import java.time.Instant;

public record FlightView(Long id, String flightNumber, Long sourceAirportId, String sourceIataCode,
        Long destinationAirportId, String destinationIataCode, Long aircraftId,
        String aircraftRegistration, Long aircraftTypeId, Instant departureTime,
        Instant arrivalTime, FlightStatus status, long version) {
}
