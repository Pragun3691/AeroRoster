package com.aeroroster.domain.scheduling;

import java.time.LocalDate;

/** Scalar database projection avoids stale managed qualification state after a lock wait. */
public record QualificationValidity(LocalDate validUntil) {
}
