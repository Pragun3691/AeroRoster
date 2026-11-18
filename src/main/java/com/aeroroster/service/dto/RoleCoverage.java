package com.aeroroster.service.dto;

import com.aeroroster.persistence.entity.CrewRole;

public record RoleCoverage(CrewRole role, int required, int active, int missing) {
}
