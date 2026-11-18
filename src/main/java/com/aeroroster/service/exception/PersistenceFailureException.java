package com.aeroroster.service.exception;

public class PersistenceFailureException extends BusinessException {
    public PersistenceFailureException(String message) {
        super(message);
    }

    public PersistenceFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
