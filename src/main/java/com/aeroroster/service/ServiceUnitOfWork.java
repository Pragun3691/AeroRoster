package com.aeroroster.service;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Separate Spring proxy lets ServiceTransactions translate errors raised during commit. */
@Component
class ServiceUnitOfWork {
    @Transactional(readOnly = true)
    public <T> T read(Supplier<T> operation) {
        return operation.get();
    }

    @Transactional
    public <T> T write(Supplier<T> operation) {
        return operation.get();
    }
}
