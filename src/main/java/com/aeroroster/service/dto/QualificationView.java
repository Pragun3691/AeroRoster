package com.aeroroster.service.dto;

import com.aeroroster.persistence.entity.CrewRole;

public record QualificationView(Long id, Long aircraftTypeId, String model, CrewRole requiredRole) {
}
