package com.aeroroster.service.exception;

public class StaleVersionException extends BusinessException {
    public StaleVersionException() {
        super("This record changed. Reload it and review your edit before retrying");
    }
}
