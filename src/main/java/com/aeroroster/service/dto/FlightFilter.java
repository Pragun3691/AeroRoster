package com.aeroroster.service.dto;

import com.aeroroster.persistence.entity.FlightStatus;
import java.time.Instant;
import jakarta.validation.constraints.Positive;
import com.aeroroster.service.validation.InputValues;

public record FlightFilter(String flightNumber, FlightStatus status, @Positive Long aircraftId,
        Instant departureFrom, Instant departureUntil) {
    public FlightFilter {
        flightNumber = InputValues.text(flightNumber);
    }
}
