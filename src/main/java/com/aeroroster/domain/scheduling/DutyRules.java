package com.aeroroster.domain.scheduling;

import com.aeroroster.domain.DutyWindow;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

import static com.aeroroster.domain.scheduling.SchedulingReasonCode.*;

@Component
public class DutyRules {
    public List<SchedulingReason> evaluate(DutyWindow proposed, List<DutyWindow> active,
            List<DutyWindow> unavailable, DutyLoad load, SchedulingPolicy policy) {
        List<SchedulingReason> reasons = new ArrayList<>();
        if (unavailable.stream().anyMatch(proposed::overlaps)) {
            reasons.add(new SchedulingReason(CREW_UNAVAILABLE, "Crew is unavailable during the proposed duty"));
        }
        if (active.stream().anyMatch(proposed::overlaps)) {
            reasons.add(new SchedulingReason(CREW_TIME_CONFLICT, "Crew already has an overlapping active duty"));
        }
        if (active.stream().anyMatch(duty -> insufficientRest(proposed, duty, policy.minimumRest()))) {
            reasons.add(new SchedulingReason(INSUFFICIENT_REST, "The gap to another active duty is shorter than the configured minimum rest"));
        }
        if (load.maximumRolling24Hours().compareTo(policy.maximumRolling24Hours()) > 0) {
            reasons.add(new SchedulingReason(ROLLING_24_HOUR_LIMIT, "Duty exceeds the configured rolling 24-hour limit"));
        }
        if (load.maximumRolling7Days().compareTo(policy.maximumRolling7Days()) > 0) {
            reasons.add(new SchedulingReason(ROLLING_7_DAY_LIMIT, "Duty exceeds the configured rolling seven-day limit"));
        }
        if (load.maximumUtcDay().compareTo(policy.maximumUtcDay()) > 0) {
            reasons.add(new SchedulingReason(UTC_DAILY_DUTY_LIMIT, "Duty exceeds the configured UTC-calendar-day limit"));
        }
        return List.copyOf(reasons);
    }

    private boolean insufficientRest(DutyWindow proposed, DutyWindow existing, Duration minimumRest) {
        if (proposed.overlaps(existing)) {
            return false;
        }
        Duration gap = existing.end().isAfter(proposed.start())
                ? Duration.between(proposed.end(), existing.start())
                : Duration.between(existing.end(), proposed.start());
        return gap.compareTo(minimumRest) < 0;
    }
}
