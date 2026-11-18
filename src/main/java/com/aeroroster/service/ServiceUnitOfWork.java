package com.aeroroster.service;

import java.util.function.Supplier;
import java.sql.Connection;
import com.aeroroster.service.exception.BusinessConflictException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Separate Spring proxy lets ServiceTransactions translate errors raised during commit. */
@Component
class ServiceUnitOfWork {
    private final JdbcTemplate jdbc;

    ServiceUnitOfWork(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public <T> T read(Supplier<T> operation) {
        return operation.get();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public <T> T write(Supplier<T> operation) {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new BusinessConflictException("Write operations cannot join a read-only transaction");
        }
        Integer isolation = jdbc.execute((ConnectionCallback<Integer>) Connection::getTransactionIsolation);
        if (isolation == null || isolation != Connection.TRANSACTION_READ_COMMITTED) {
            throw new BusinessConflictException("Write operations require READ_COMMITTED transaction isolation");
        }
        return operation.get();
    }
}
