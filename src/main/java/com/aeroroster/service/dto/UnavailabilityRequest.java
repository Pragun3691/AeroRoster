package com.aeroroster.service.dto;

import com.aeroroster.service.validation.InputValues;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UnavailabilityRequest(@NotNull Instant startTime, @NotNull Instant endTime,
        @NotBlank @Size(max = 200) String reason) {
    public UnavailabilityRequest {
        reason = InputValues.text(reason);
    }
}
