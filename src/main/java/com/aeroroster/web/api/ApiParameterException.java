package com.aeroroster.web.api;

import com.aeroroster.service.exception.InvalidInputException;

public class ApiParameterException extends InvalidInputException {
    private final String field;

    public ApiParameterException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
