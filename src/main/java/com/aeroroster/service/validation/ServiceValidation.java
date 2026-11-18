package com.aeroroster.service.validation;

import com.aeroroster.service.exception.InvalidInputException;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class ServiceValidation {
    private final Validator validator;

    public ServiceValidation(Validator validator) {
        this.validator = validator;
    }

    public <T> T request(T request) {
        if (request == null) {
            throw new InvalidInputException("Request is required");
        }
        String errors = validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .sorted().collect(Collectors.joining(", "));
        if (!errors.isEmpty()) {
            throw new InvalidInputException(errors);
        }
        return request;
    }

    public void id(Long id) {
        if (id == null || id <= 0) {
            throw new InvalidInputException("ID must be a positive number");
        }
    }

    public void pageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged() || pageable.getPageSize() > 200) {
            throw new InvalidInputException("A page request with size 1 to 200 is required");
        }
        // Service queries define stable ordering and avoid exposing entity property paths.
        if (pageable.getSort().isSorted()) {
            throw new InvalidInputException("Use the service's default ordering");
        }
    }

    public void window(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new InvalidInputException("Arrival/end must be after departure/start");
        }
        // PostgreSQL TIMESTAMPTZ stores microseconds. Reject precision loss before conflict checks.
        if (start.getNano() % 1000 != 0 || end.getNano() % 1000 != 0) {
            throw new InvalidInputException("Timestamps must have at most microsecond precision");
        }
    }
}
