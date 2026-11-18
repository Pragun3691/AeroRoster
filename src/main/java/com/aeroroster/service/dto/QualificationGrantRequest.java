package com.aeroroster.service.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record QualificationGrantRequest(@NotNull @Positive Long qualificationId, LocalDate validUntil) {
}
