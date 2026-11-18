package com.aeroroster.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** An immutable [start, end) interval. Touching endpoints are not an overlap. */
public record DutyWindow(Instant start, Instant end) {
    public DutyWindow {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Duty end must be after start");
        }
    }

    public boolean overlaps(DutyWindow other) {
        Objects.requireNonNull(other, "other");
        return start.isBefore(other.end) && end.isAfter(other.start);
    }

    public Duration duration() {
        return Duration.between(start, end);
    }
}
