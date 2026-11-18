package com.aeroroster.domain.scheduling;

import java.time.Duration;

public record DutyLoad(Duration maximumRolling24Hours, Duration maximumRolling7Days,
        Duration maximumUtcDay) {
}
