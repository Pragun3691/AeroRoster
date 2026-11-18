package com.aeroroster.service.dto;

import com.aeroroster.persistence.entity.CrewRole;

public record CrewFilter(String query, CrewRole role, Boolean active) {
}
