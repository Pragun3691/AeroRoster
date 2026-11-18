package com.aeroroster.web.api;

import com.aeroroster.service.exception.*;
import com.aeroroster.web.api.dto.ApiError;
import com.aeroroster.web.api.dto.ApiFieldError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ApiErrors errors;

    public ApiExceptionHandler(ApiErrors errors) {
        this.errors = errors;
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> validation(BindException exception, HttpServletRequest request) {
        List<ApiFieldError> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiFieldError(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(ApiFieldError::field)).toList();
        return errors.response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, fields, null);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> methodValidation(HandlerMethodValidationException exception, HttpServletRequest request) {
        List<ApiFieldError> fields = exception.getParameterValidationResults().stream().flatMap(result ->
                result.getResolvableErrors().stream().map(error -> new ApiFieldError(
                        result.getMethodParameter().getParameterName(), error.getDefaultMessage()))).toList();
        return errors.response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, fields, null);
    }

    @ExceptionHandler(ApiParameterException.class)
    public ResponseEntity<ApiError> parameter(ApiParameterException exception, HttpServletRequest request) {
        return errors.response(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", exception.getMessage(), request,
                List.of(new ApiFieldError(exception.getField(), exception.getMessage())), null);
    }

    @ExceptionHandler(ApiPreconditions.RequiredPreconditionException.class)
    public ResponseEntity<ApiError> precondition(HttpServletRequest request) {
        return errors.response(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED",
                "Send X-Expected-Version using the original resource id:version", request, List.of(), null);
    }

    @ExceptionHandler(StaleVersionException.class)
    public ResponseEntity<ApiError> stale(StaleVersionException exception, HttpServletRequest request) {
        return errors.response(HttpStatus.CONFLICT, "STALE_VERSION", exception.getMessage(), request, List.of(), null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> conversion(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return errors.response(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Parameter has an invalid type or value", request,
                List.of(new ApiFieldError(exception.getName(), "Use the documented parameter type and enum values")), null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException exception, HttpServletRequest request) {
        return errors.response(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Required parameter is missing", request,
                List.of(new ApiFieldError(exception.getParameterName(), "Parameter is required")), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> json(HttpServletRequest request) {
        return errors.response(HttpStatus.BAD_REQUEST, "MALFORMED_JSON",
                "JSON contains missing, unknown or invalid fields. Use the documented types and ISO-8601 dates", request,
                List.of(new ApiFieldError("body", "Provide a valid JSON object using the documented schema")), null);
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ApiError> invalid(InvalidInputException exception, HttpServletRequest request) {
        return errors.response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage(), request, List.of(), null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return errors.response(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage(), request, List.of(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> unknownRoute(HttpServletRequest request) {
        return errors.response(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource was not found", request, List.of(), null);
    }

    @ExceptionHandler(AssignmentRejectedException.class)
    public ResponseEntity<ApiError> assignment(AssignmentRejectedException exception, HttpServletRequest request) {
        return errors.response(HttpStatus.CONFLICT, "ASSIGNMENT_REJECTED", "Assignment does not satisfy the scheduling rules",
                request, List.of(), exception.getDecision());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> duplicate(DuplicateResourceException exception, HttpServletRequest request) {
        return errors.response(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", exception.getMessage(), request, List.of(), null);
    }

    @ExceptionHandler(BusinessConflictException.class)
    public ResponseEntity<ApiError> conflict(BusinessConflictException exception, HttpServletRequest request) {
        return errors.response(HttpStatus.CONFLICT, "BUSINESS_CONFLICT", exception.getMessage(), request, List.of(), null);
    }

    @ExceptionHandler(PersistenceFailureException.class)
    public ResponseEntity<ApiError> persistence(PersistenceFailureException exception, HttpServletRequest request) {
        return internal(exception, request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> domain(HttpServletRequest request) {
        return errors.response(HttpStatus.CONFLICT, "DOMAIN_CONFLICT", "Operation conflicts with the current application state", request, List.of(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        ResponseEntity<ApiError> response = errors.response(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "HTTP method is not supported for this resource", request, List.of(), null);
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
                .header("Allow", String.join(", ", exception.getSupportedMethods())).body(response.getBody());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> contentType(HttpServletRequest request) {
        return errors.response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Use Content-Type application/json", request, List.of(), null);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiError> accept(HttpServletRequest request) {
        return errors.response(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "This API produces application/json", request, List.of(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        return internal(exception, request);
    }

    private ResponseEntity<ApiError> internal(Exception exception, HttpServletRequest request) {
        log.error("API operation failed at {}", request.getRequestURI(), exception);
        return errors.response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The request could not be completed", request, List.of(), null);
    }
}
