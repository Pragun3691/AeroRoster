package com.aeroroster.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import com.aeroroster.service.validation.InputValues;

public record AircraftRequest(@NotBlank @Size(max = 24) String registration,
        @NotNull @Positive Long aircraftTypeId) {
    public AircraftRequest {
        registration = InputValues.identifier(registration);
    }
}
