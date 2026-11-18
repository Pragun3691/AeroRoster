package com.aeroroster.service.exception;

import com.aeroroster.service.dto.EligibilityDecision;

public class AssignmentRejectedException extends BusinessConflictException {
    private final EligibilityDecision decision;

    public AssignmentRejectedException(EligibilityDecision decision) {
        super("Assignment rejected. " + decision.reasons().stream()
                .map(reason -> reason.explanation()).collect(java.util.stream.Collectors.joining(" ")));
        this.decision = decision;
    }

    public EligibilityDecision getDecision() {
        return decision;
    }
}
