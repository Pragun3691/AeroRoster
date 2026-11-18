package com.aeroroster.service.dto;

import java.time.Instant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import com.aeroroster.service.validation.InputValues;

public record FlightRequest(@NotBlank @Size(max = 15) String flightNumber,
        @NotNull @Positive Long sourceAirportId,
        @NotNull @Positive Long destinationAirportId,
        @NotNull @Positive Long aircraftId,
        @NotNull Instant departureTime,
        @NotNull Instant arrivalTime) {
    public FlightRequest {
        flightNumber = InputValues.identifier(flightNumber);
    }
}
