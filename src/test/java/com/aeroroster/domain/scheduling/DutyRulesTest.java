package com.aeroroster.domain.scheduling;

import com.aeroroster.domain.DutyWindow;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static com.aeroroster.domain.scheduling.SchedulingReasonCode.*;
import static org.junit.jupiter.api.Assertions.*;

class DutyRulesTest {
    private final DutyLoadCalculator calculator = new DutyLoadCalculator();
    private final DutyRules rules = new DutyRules();
    private final SchedulingPolicy policy = new SchedulingPolicy(Duration.ofHours(1), Duration.ofMinutes(30),
            Duration.ofHours(10), Duration.ofHours(10), Duration.ofHours(40), Duration.ofHours(14));

    @Test
    void utcDayCapRemainsIndependentWithRelaxedRollingCaps() {
        SchedulingPolicy independent = new SchedulingPolicy(Duration.ofHours(1), Duration.ofMinutes(30),
                Duration.ofHours(10), Duration.ofHours(24), Duration.ofHours(168), Duration.ofHours(14));
        DutyWindow exact = window("2026-12-01T00:00:00Z", "2026-12-01T14:00:00Z");
        assertTrue(rules.evaluate(exact, List.of(), List.of(), calculator.assess(exact, List.of()), independent).isEmpty());
        DutyWindow excess = new DutyWindow(exact.start(), exact.end().plusNanos(1000));
        assertEquals(List.of(UTC_DAILY_DUTY_LIMIT), rules.evaluate(excess, List.of(), List.of(),
                calculator.assess(excess, List.of()), independent).stream().map(SchedulingReason::code).toList());
        DutyWindow midnight = window("2026-12-01T17:00:00Z", "2026-12-02T07:30:00Z");
        assertEquals(Duration.ofMinutes(450), calculator.assess(midnight, List.of()).maximumUtcDay());
        assertTrue(rules.evaluate(midnight, List.of(), List.of(), calculator.assess(midnight, List.of()), independent).isEmpty());
    }

    private DutyWindow window(String start, String end) {
        return new DutyWindow(Instant.parse(start), Instant.parse(end));
    }

    private List<SchedulingReasonCode> codes(DutyWindow proposed, List<DutyWindow> existing) {
        return rules.evaluate(proposed, existing, List.of(), calculator.assess(proposed, existing), policy)
                .stream().map(SchedulingReason::code).toList();
    }

    @Test
    void derivedDutyWindowEnclosesFlightAndRejectsInvalidTimes() {
        Instant start = Instant.parse("2026-12-01T05:00:00Z");
        DutyWindow window = policy.dutyWindow(start, start.plusSeconds(7200));
        assertEquals(start.minusSeconds(3600), window.start());
        assertEquals(start.plusSeconds(9000), window.end());
        assertThrows(IllegalArgumentException.class, () -> policy.dutyWindow(start, start));
        assertThrows(IllegalArgumentException.class, () -> policy.dutyWindow(start, start.minusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> policy.dutyWindow(start, start.plusNanos(1)));
    }

    @Test
    void adjacentIntervalsDoNotOverlapButRequireRest() {
        DutyWindow first = window("2026-12-01T00:00:00Z", "2026-12-01T02:00:00Z");
        DutyWindow next = window("2026-12-01T02:00:00Z", "2026-12-01T04:00:00Z");
        assertFalse(first.overlaps(next));
        assertEquals(List.of(INSUFFICIENT_REST), codes(next, List.of(first)));
    }

    @Test
    void exactRestBoundaryWorksBeforeAndAfterProposedDuty() {
        DutyWindow proposed = window("2026-12-01T12:00:00Z", "2026-12-01T14:00:00Z");
        List<DutyWindow> existing = List.of(
                window("2026-12-01T00:00:00Z", "2026-12-01T02:00:00Z"),
                window("2026-12-02T00:00:00Z", "2026-12-02T02:00:00Z"));
        assertTrue(codes(proposed, existing).isEmpty());
        DutyWindow tooClose = window("2026-12-01T11:59:59.999999Z", "2026-12-01T14:00:00Z");
        assertTrue(codes(tooClose, existing).contains(INSUFFICIENT_REST));
    }

    @Test
    void overlappingAndSeparateRestConflictsAreReportedTogether() {
        DutyWindow proposed = window("2026-12-01T12:00:00Z", "2026-12-01T14:00:00Z");
        List<SchedulingReasonCode> codes = codes(proposed, List.of(
                window("2026-12-01T11:00:00Z", "2026-12-01T13:00:00Z"),
                window("2026-12-01T15:00:00Z", "2026-12-01T16:00:00Z")));
        assertTrue(codes.contains(CREW_TIME_CONFLICT));
        assertTrue(codes.contains(INSUFFICIENT_REST));
    }

    @Test
    void rolling24HourMaximumClipsPartialDuties() {
        DutyWindow existing = window("2026-12-01T00:00:00Z", "2026-12-01T10:00:00Z");
        DutyWindow proposed = window("2026-12-01T20:00:00Z", "2026-12-02T06:00:00Z");
        assertEquals(Duration.ofHours(14), calculator.assess(proposed, List.of(existing)).maximumRolling24Hours());
        assertTrue(codes(proposed, List.of(existing)).contains(ROLLING_24_HOUR_LIMIT));
    }

    @Test
    void rollingSevenDayMaximumClipsPartialDuties() {
        DutyWindow existing = window("2026-12-01T00:00:00Z", "2026-12-02T00:00:00Z");
        DutyWindow proposed = window("2026-12-07T12:00:00Z", "2026-12-08T12:00:00Z");
        assertEquals(Duration.ofHours(36), calculator.assess(proposed, List.of(existing)).maximumRolling7Days());
    }

    @Test
    void rollingSevenDayLimitAccumulatesSeparatedDuties() {
        Instant start = Instant.parse("2026-12-01T00:00:00Z");
        List<DutyWindow> existing = new ArrayList<>();
        for (int day = 0; day < 5; day++) {
            existing.add(new DutyWindow(start.plus(Duration.ofDays(day)), start.plus(Duration.ofDays(day)).plus(Duration.ofHours(8))));
        }
        DutyWindow proposed = new DutyWindow(start.plus(Duration.ofDays(5)), start.plus(Duration.ofDays(5)).plus(Duration.ofHours(8)));
        assertEquals(Duration.ofHours(48), calculator.assess(proposed, existing).maximumRolling7Days());
        assertEquals(List.of(ROLLING_7_DAY_LIMIT), codes(proposed, existing));
    }

    @Test
    void crossingMidnightSplitsUtcDailyCharge() {
        DutyWindow proposed = window("2026-12-01T22:00:00Z", "2026-12-02T02:00:00Z");
        DutyLoad load = calculator.assess(proposed, List.of());
        assertEquals(Duration.ofHours(4), load.maximumRolling24Hours());
        assertEquals(Duration.ofHours(4), load.maximumRolling7Days());
        assertEquals(Duration.ofHours(2), load.maximumUtcDay());
    }

    @Test
    void multiDateDutyCountsInteriorDays() {
        DutyWindow proposed = window("2026-12-01T12:00:00Z", "2026-12-04T12:00:00Z");
        DutyLoad load = calculator.assess(proposed, List.of());
        assertEquals(Duration.ofHours(24), load.maximumRolling24Hours());
        assertEquals(Duration.ofHours(72), load.maximumRolling7Days());
        assertEquals(Duration.ofHours(24), load.maximumUtcDay());
        assertTrue(codes(proposed, List.of()).contains(UTC_DAILY_DUTY_LIMIT));
    }

    @Test
    void limitsUseExactDurationIncludingMicroseconds() {
        DutyWindow boundary = window("2026-12-01T00:00:00Z", "2026-12-01T10:00:00Z");
        assertTrue(codes(boundary, List.of()).isEmpty());
        DutyWindow excessive = new DutyWindow(boundary.start(), boundary.end().plusNanos(1000));
        assertEquals(List.of(ROLLING_24_HOUR_LIMIT), codes(excessive, List.of()));
    }

    @Test
    void unrelatedDistantHistoryDoesNotPoisonNewDuty() {
        DutyWindow proposed = window("2026-12-20T00:00:00Z", "2026-12-20T02:00:00Z");
        assertTrue(codes(proposed, List.of(window("2026-11-01T00:00:00Z", "2026-11-05T00:00:00Z"))).isEmpty());
    }

    @Test
    void unavailableEndpointsRemainHalfOpen() {
        DutyWindow proposed = window("2026-12-01T02:00:00Z", "2026-12-01T04:00:00Z");
        DutyLoad load = calculator.assess(proposed, List.of());
        assertTrue(rules.evaluate(proposed, List.of(), List.of(window("2026-12-01T00:00:00Z", "2026-12-01T02:00:00Z")), load, policy).isEmpty());
        assertEquals(CREW_UNAVAILABLE, rules.evaluate(proposed, List.of(), List.of(window("2026-12-01T03:00:00Z", "2026-12-01T05:00:00Z")), load, policy).getFirst().code());
    }

    @Test
    void extremaAlgorithmMatchesIndependentGridOracle() {
        Random random = new Random(2301);
        Instant epoch = Instant.parse("2026-12-01T00:00:00Z");
        for (int sample = 0; sample < 40; sample++) {
            List<DutyWindow> existing = new ArrayList<>();
            for (int index = 0; index < 7; index++) {
                Instant start = epoch.plusSeconds(random.nextInt(12 * 96) * 900L);
                existing.add(new DutyWindow(start, start.plusSeconds((1 + random.nextInt(180)) * 900L)));
            }
            Instant start = epoch.plusSeconds((4 * 96 + random.nextInt(3 * 96)) * 900L);
            DutyWindow proposed = new DutyWindow(start, start.plusSeconds((1 + random.nextInt(200)) * 900L));
            List<DutyWindow> all = new ArrayList<>(existing);
            all.add(proposed);
            DutyLoad actual = calculator.assess(proposed, existing);
            assertEquals(oracleRolling(proposed, all, Duration.ofHours(24)), actual.maximumRolling24Hours());
            assertEquals(oracleRolling(proposed, all, Duration.ofDays(7)), actual.maximumRolling7Days());
            Duration maximumDay = Duration.ZERO;
            for (LocalDate date = proposed.start().atZone(ZoneOffset.UTC).toLocalDate();
                    date.atStartOfDay(ZoneOffset.UTC).toInstant().isBefore(proposed.end()); date = date.plusDays(1)) {
                Instant day = date.atStartOfDay(ZoneOffset.UTC).toInstant();
                Duration load = oracleTotal(all, day, day.plus(Duration.ofDays(1)));
                if (load.compareTo(maximumDay) > 0) maximumDay = load;
            }
            assertEquals(maximumDay, actual.maximumUtcDay());
        }
    }

    private Duration oracleRolling(DutyWindow proposed, List<DutyWindow> all, Duration period) {
        Duration maximum = Duration.ZERO;
        for (Instant start = proposed.start().minus(period); !start.isAfter(proposed.end()); start = start.plusSeconds(900)) {
            Duration load = oracleTotal(all, start, start.plus(period));
            if (load.compareTo(maximum) > 0) maximum = load;
        }
        return maximum;
    }

    private Duration oracleTotal(List<DutyWindow> all, Instant start, Instant end) {
        long seconds = 0;
        for (DutyWindow duty : all) {
            long from = Math.max(start.getEpochSecond(), duty.start().getEpochSecond());
            long until = Math.min(end.getEpochSecond(), duty.end().getEpochSecond());
            seconds += Math.max(0, until - from);
        }
        return Duration.ofSeconds(seconds);
    }
}
