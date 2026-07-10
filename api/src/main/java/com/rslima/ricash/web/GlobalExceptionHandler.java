package com.rslima.ricash.web;

import com.rslima.ricash.exceptions.ConflictException;
import com.rslima.ricash.exceptions.EntityNotFoundException;

import com.toedter.spring.hateoas.jsonapi.JsonApiError;
import com.toedter.spring.hateoas.jsonapi.JsonApiErrors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Renders every application error as a JSON:API error document with one
 * consistent status mapping: EntityNotFoundException -> 404,
 * ConflictException -> 409, IllegalArgumentException -> 400, bean-validation
 * failures -> 400 (one error per field), anything unexpected -> 500.
 * Extending ResponseEntityExceptionHandler keeps Spring MVC's own exceptions
 * (malformed JSON, unsupported method, ...) on their proper status codes.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<JsonApiErrors> handleNotFound(EntityNotFoundException ex) {
        return error(NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<JsonApiErrors> handleConflict(ConflictException ex) {
        return error(CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<JsonApiErrors> handleIllegalArgument(IllegalArgumentException ex) {
        return error(BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<JsonApiErrors> handleUnexpected(Exception ex) throws Exception {
        // Security exceptions must reach Spring Security's handling untouched.
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        log.error("Unhandled exception", ex);
        return error(INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        var errors = JsonApiErrors.create();
        ex.getBindingResult().getFieldErrors().forEach(fieldError -> errors.withError(
                JsonApiError.create()
                        .withStatus(Integer.toString(BAD_REQUEST.value()))
                        .withTitle(BAD_REQUEST.getReasonPhrase())
                        .withDetail(fieldError.getField() + ": " + fieldError.getDefaultMessage())
                        .withSourcePointer("/data/attributes/" + fieldError.getField())));
        ex.getBindingResult().getGlobalErrors().forEach(globalError -> errors.withError(
                JsonApiError.create()
                        .withStatus(Integer.toString(BAD_REQUEST.value()))
                        .withTitle(BAD_REQUEST.getReasonPhrase())
                        .withDetail(globalError.getDefaultMessage())));

        return ResponseEntity.status(BAD_REQUEST).body(errors);
    }

    private ResponseEntity<JsonApiErrors> error(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(
                JsonApiErrors.create().withError(
                        JsonApiError.create()
                                .withStatus(Integer.toString(status.value()))
                                .withTitle(status.getReasonPhrase())
                                .withDetail(detail)));
    }
}
