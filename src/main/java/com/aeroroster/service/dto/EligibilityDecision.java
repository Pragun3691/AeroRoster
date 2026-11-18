package com.aeroroster.service.dto;

import com.aeroroster.domain.DutyWindow;
import com.aeroroster.domain.scheduling.DutyLoad;
import com.aeroroster.domain.scheduling.SchedulingPolicy;
import com.aeroroster.domain.scheduling.SchedulingReason;
import java.util.List;

public record EligibilityDecision(boolean eligible, DutyWindow dutyWindow, DutyLoad dutyLoad,
        List<SchedulingReason> reasons, SchedulingPolicy policy, String policyNotice) {
    public EligibilityDecision {
        reasons = List.copyOf(reasons);
        if (eligible != reasons.isEmpty()) {
            throw new IllegalArgumentException("Eligibility must agree with the reasons");
        }
    }
}
