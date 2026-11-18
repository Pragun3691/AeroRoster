package com.aeroroster.web.api;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.data.domain.PageRequest;

/** HTTP syntax and paging only. Domain validation remains in the services. */
final class ApiParameters {
    private ApiParameters() {
    }

    static PageRequest page(int page, int size) {
        if (page < 0) {
            throw new ApiParameterException("page", "Page must be zero or greater");
        }
        if (size < 1 || size > 200) {
            throw new ApiParameterException("size", "Size must be between 1 and 200");
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new ApiParameterException("page", "Page times size must not exceed 2147483647");
        }
        return PageRequest.of(page, size);
    }

    static Boolean optionalBoolean(String value, String field) {
        if (value == null) {
            return null;
        }
        if (!value.equals("true") && !value.equals("false")) {
            throw new ApiParameterException(field, "Value must be true or false");
        }
        return Boolean.valueOf(value);
    }

    static Instant optionalInstant(String value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeException exception) {
            throw new ApiParameterException(field, "Timestamp must use ISO-8601 with Z or an explicit UTC offset");
        }
    }
}
