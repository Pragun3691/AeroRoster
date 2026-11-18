package com.aeroroster.web.api;

import com.aeroroster.service.ExpectedVersion;

/** Conditional command header, independent of representation caching/derived display fields. */
final class ApiPreconditions {
    static final String HEADER = "X-Expected-Version";

    private ApiPreconditions() { }

    static ExpectedVersion require(String header) {
        if (header == null) throw new RequiredPreconditionException();
        if (!header.matches("[1-9][0-9]*:(0|[1-9][0-9]*)")) {
            throw new ApiParameterException(HEADER, "Use the original resource id:version");
        }
        String[] parts = header.split(":");
        try {
            return new ExpectedVersion(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException exception) {
            throw new ApiParameterException(HEADER, "Resource id and version must fit int64");
        }
    }

    static final class RequiredPreconditionException extends RuntimeException { }
}
