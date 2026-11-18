package com.aeroroster.service.dto;

import java.time.LocalDate;

public record QualificationExpiryRequest(LocalDate validUntil) {
}
