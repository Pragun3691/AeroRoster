package com.aeroroster.web.api;

import com.aeroroster.service.dto.EligibilityDecision;
import com.aeroroster.web.api.dto.ApiError;
import com.aeroroster.web.api.dto.ApiFieldError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ApiErrors {
    private final Clock clock;

    public ApiErrors(Clock clock) {
        this.clock = clock;
    }

    ApiError body(String code, String message, HttpServletRequest request, List<ApiFieldError> fields,
            EligibilityDecision eligibility) {
        return new ApiError(code, message, clock.instant(), request.getRequestURI(), List.copyOf(fields), eligibility);
    }

    ResponseEntity<ApiError> response(HttpStatus status, String code, String message, HttpServletRequest request,
            List<ApiFieldError> fields, EligibilityDecision eligibility) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(body(code, message, request, fields, eligibility));
    }
}
