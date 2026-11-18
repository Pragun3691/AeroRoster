package com.aeroroster.service.dto;

import jakarta.validation.constraints.Positive;

public record AircraftFilter(String query, Boolean active, @Positive Long aircraftTypeId) {
}
