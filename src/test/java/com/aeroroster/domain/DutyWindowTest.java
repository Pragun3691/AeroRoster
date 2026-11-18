package com.aeroroster.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class DutyWindowTest {
    private DutyWindow window(String start, String end) {
        return new DutyWindow(Instant.parse(start), Instant.parse(end));
    }

    @Test void detectsOverlap() {
        DutyWindow first = window("2026-12-01T04:00:00Z", "2026-12-01T08:00:00Z");
        DutyWindow second = window("2026-12-01T07:59:00Z", "2026-12-01T10:00:00Z");
        assertTrue(first.overlaps(second));
        assertTrue(second.overlaps(first));
    }

    @Test void touchingEndpointsAreAllowed() {
        DutyWindow first = window("2026-12-01T04:00:00Z", "2026-12-01T08:00:00Z");
        DutyWindow second = window("2026-12-01T08:00:00Z", "2026-12-01T10:00:00Z");
        assertFalse(first.overlaps(second));
    }

    @Test void rejectsNonPositiveInterval() {
        assertThrows(IllegalArgumentException.class, () ->
            window("2026-12-01T10:00:00Z", "2026-12-01T10:00:00Z"));
    }
}
