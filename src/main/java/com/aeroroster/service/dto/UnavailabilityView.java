package com.aeroroster.service.dto;

import java.time.Instant;

public record UnavailabilityView(Long id, Long crewId, Instant startTime, Instant endTime, String reason) {
}
