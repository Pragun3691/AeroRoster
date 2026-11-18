package com.aeroroster.service.dto;

import com.aeroroster.persistence.entity.CrewRole;
import java.time.LocalDate;

public record CrewQualificationView(Long id, Long crewId, Long qualificationId,
        Long aircraftTypeId, String model, CrewRole requiredRole, LocalDate validUntil, long version) {
}
