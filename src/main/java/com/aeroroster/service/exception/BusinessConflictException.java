package com.aeroroster.service.exception;

public class BusinessConflictException extends BusinessException {
    public BusinessConflictException(String message) {
        super(message);
    }

    public BusinessConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
