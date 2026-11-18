package com.aeroroster.domain.scheduling;

/** Immutable assignment ownership used to acquire parent locks in the global order. */
public record AssignmentIdentity(Long crewId, Long flightId) {
}
