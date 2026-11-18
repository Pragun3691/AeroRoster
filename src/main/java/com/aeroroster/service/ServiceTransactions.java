package com.aeroroster.service;

import com.aeroroster.service.exception.BusinessConflictException;
import com.aeroroster.service.exception.DuplicateResourceException;
import com.aeroroster.service.exception.PersistenceFailureException;
import java.sql.SQLException;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

/** Covers commit as well as repository calls with a consistent business error boundary. */
@Component
public class ServiceTransactions {
    private final ServiceUnitOfWork unitOfWork;

    public ServiceTransactions(ServiceUnitOfWork unitOfWork) {
        this.unitOfWork = unitOfWork;
    }

    public <T> T read(Supplier<T> operation) {
        return execute(() -> unitOfWork.read(operation));
    }

    public <T> T write(Supplier<T> operation) {
        return execute(() -> unitOfWork.write(operation));
    }

    private <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataIntegrityViolationException exception) {
            if (hasSqlState(exception, "23505")) {
                throw new DuplicateResourceException("A record with these unique identifiers already exists", exception);
            }
            throw new BusinessConflictException("The operation conflicts with existing records or database constraints", exception);
        } catch (PessimisticLockingFailureException exception) {
            throw new BusinessConflictException("A concurrent change prevented this operation. Retry the complete operation", exception);
        } catch (DataAccessException | TransactionException exception) {
            throw new PersistenceFailureException("The database operation could not be completed", exception);
        }
    }

    private boolean hasSqlState(Throwable exception, String state) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && state.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
