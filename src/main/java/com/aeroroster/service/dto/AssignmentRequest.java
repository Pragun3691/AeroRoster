package com.aeroroster.service.dto;

import com.aeroroster.persistence.entity.CrewRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssignmentRequest(@NotNull @Positive Long crewId,
        @NotNull @Positive Long flightId, @NotNull CrewRole assignedRole) {
}
