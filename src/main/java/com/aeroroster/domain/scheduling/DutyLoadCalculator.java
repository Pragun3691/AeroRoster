package com.aeroroster.domain.scheduling;

import com.aeroroster.domain.DutyWindow;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Exact interval clipping with Duration arithmetic and a finite set of extrema. */
@Component
public class DutyLoadCalculator {
    public DutyLoad assess(DutyWindow proposed, List<DutyWindow> existing) {
        List<DutyWindow> all = new ArrayList<>(existing);
        all.add(proposed);
        return new DutyLoad(maximumRolling(all, proposed, Duration.ofHours(24)),
                maximumRolling(all, proposed, Duration.ofDays(7)), maximumUtcDay(all, proposed));
    }

    private Duration maximumRolling(List<DutyWindow> duties, DutyWindow proposed, Duration period) {
        Set<Instant> starts = new HashSet<>();
        for (DutyWindow duty : duties) {
            // Load is piecewise linear. Its slope changes only at these four boundaries.
            starts.add(duty.start());
            starts.add(duty.end());
            starts.add(duty.start().minus(period));
            starts.add(duty.end().minus(period));
        }
        Duration maximum = Duration.ZERO;
        for (Instant start : starts) {
            Instant end = start.plus(period);
            if (!end.isBefore(proposed.start()) && !start.isAfter(proposed.end())) {
                Duration load = total(duties, start, end);
                if (load.compareTo(maximum) > 0) {
                    maximum = load;
                }
            }
        }
        return maximum;
    }

    private Duration maximumUtcDay(List<DutyWindow> duties, DutyWindow proposed) {
        Set<LocalDate> dates = new HashSet<>();
        for (DutyWindow duty : duties) {
            LocalDate first = duty.start().atZone(ZoneOffset.UTC).toLocalDate();
            dates.add(first);
            dates.add(first.plusDays(1));
            dates.add(duty.end().minusNanos(1).atZone(ZoneOffset.UTC).toLocalDate());
        }
        Duration maximum = Duration.ZERO;
        for (LocalDate date : dates) {
            Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant end = start.plus(Duration.ofDays(1));
            if (start.isBefore(proposed.end()) && end.isAfter(proposed.start())) {
                Duration load = total(duties, start, end);
                if (load.compareTo(maximum) > 0) {
                    maximum = load;
                }
            }
        }
        return maximum;
    }

    private Duration total(List<DutyWindow> duties, Instant start, Instant end) {
        Duration total = Duration.ZERO;
        for (DutyWindow duty : duties) {
            Instant clippedStart = duty.start().isAfter(start) ? duty.start() : start;
            Instant clippedEnd = duty.end().isBefore(end) ? duty.end() : end;
            if (clippedEnd.isAfter(clippedStart)) {
                total = total.plus(Duration.between(clippedStart, clippedEnd));
            }
        }
        return total;
    }
}
