package com.aeroroster.service.dto;

public record AircraftView(Long id, String registration, Long aircraftTypeId, String model, boolean active) {
}
