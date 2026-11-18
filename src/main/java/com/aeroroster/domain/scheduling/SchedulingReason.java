package com.aeroroster.domain.scheduling;

import java.util.Objects;

public record SchedulingReason(SchedulingReasonCode code, String explanation) {
    public SchedulingReason {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(explanation, "explanation");
    }
}
