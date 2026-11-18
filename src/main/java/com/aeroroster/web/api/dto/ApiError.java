package com.aeroroster.web.api.dto;

import java.time.Instant;
import java.util.List;
import com.aeroroster.service.dto.EligibilityDecision;

public record ApiError(String code, String message, Instant timestamp, String path,
        List<ApiFieldError> fieldErrors, EligibilityDecision eligibility) {
}
