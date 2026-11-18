package com.aeroroster.service.dto;

import com.aeroroster.domain.scheduling.SchedulingReason;
import com.aeroroster.persistence.entity.FlightStatus;
import java.util.List;

public record FlightCrewCoverage(Long flightId, FlightStatus flightStatus, List<RoleCoverage> roles,
        boolean minimumStaffingSatisfied, List<SchedulingReason> reasons, String policyNotice) {
    public FlightCrewCoverage {
        roles = List.copyOf(roles);
        reasons = List.copyOf(reasons);
    }
}
