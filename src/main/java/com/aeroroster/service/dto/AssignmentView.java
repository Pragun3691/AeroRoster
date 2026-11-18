package com.aeroroster.service.dto;

import com.aeroroster.persistence.entity.CrewRole;
import com.aeroroster.persistence.entity.AssignmentStatus;
import java.time.Instant;

public record AssignmentView(Long id, Long crewId, Long flightId, String flightNumber, CrewRole assignedRole,
        Instant dutyStartTime, Instant dutyEndTime, AssignmentStatus status, Instant createdAt) {
}
