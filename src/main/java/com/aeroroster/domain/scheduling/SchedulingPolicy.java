package com.aeroroster.domain.scheduling;

import java.time.Duration;
import java.time.Instant;
import com.aeroroster.domain.DutyWindow;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("aeroroster.scheduling")
public record SchedulingPolicy(
        @DefaultValue("PT1H") Duration preflightBuffer,
        @DefaultValue("PT30M") Duration postflightBuffer,
        @DefaultValue("PT10H") Duration minimumRest,
        @DefaultValue("PT10H") Duration maximumRolling24Hours,
        @DefaultValue("PT40H") Duration maximumRolling7Days,
        @DefaultValue("PT14H") Duration maximumUtcDay) {
    public static final String NOTICE = "Illustrative academic scheduling policy. Not certified aviation regulations.";

    public SchedulingPolicy {
        nonnegative(preflightBuffer, "preflightBuffer");
        nonnegative(postflightBuffer, "postflightBuffer");
        nonnegative(minimumRest, "minimumRest");
        positive(maximumRolling24Hours, "maximumRolling24Hours");
        positive(maximumRolling7Days, "maximumRolling7Days");
        positive(maximumUtcDay, "maximumUtcDay");
    }

    public DutyWindow dutyWindow(Instant departure, Instant arrival) {
        Objects.requireNonNull(departure, "departure");
        Objects.requireNonNull(arrival, "arrival");
        if (!arrival.isAfter(departure) || departure.getNano() % 1000 != 0 || arrival.getNano() % 1000 != 0) {
            throw new IllegalArgumentException("Flight times must increase and use at most microsecond precision");
        }
        DutyWindow window = new DutyWindow(departure.minus(preflightBuffer), arrival.plus(postflightBuffer));
        if (window.start().isAfter(departure) || window.end().isBefore(arrival)) {
            throw new IllegalArgumentException("Duty must enclose the flight");
        }
        return window;
    }

    private static void nonnegative(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative() || duration.getNano() % 1000 != 0) {
            throw new IllegalArgumentException(name + " must be nonnegative with at most microsecond precision");
        }
    }

    private static void positive(Duration duration, String name) {
        nonnegative(duration, name);
        if (duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
