package com.aeroroster.web.api.dto;

import jakarta.validation.constraints.NotNull;
import com.aeroroster.persistence.entity.FlightStatus;

public record FlightStatusRequest(@NotNull FlightStatus status) {
}
