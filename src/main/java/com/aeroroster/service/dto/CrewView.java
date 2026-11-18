package com.aeroroster.service.dto;

import com.aeroroster.persistence.entity.CrewRole;

public record CrewView(Long id, String name, CrewRole role, String licenseNo,
        int experienceYears, String contactNo, boolean active) {
}
