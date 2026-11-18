package com.aeroroster.service.exception;

public class DuplicateResourceException extends BusinessConflictException {
    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
