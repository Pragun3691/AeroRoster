package com.aeroroster.web.api.dto;

import jakarta.validation.constraints.NotNull;

public record ActivityRequest(@NotNull Boolean active) {
}
