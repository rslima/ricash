package com.rslima.ricash.configuration;

import com.rslima.ricash.exceptions.EntityNotFoundException;
import com.rslima.ricash.ledgers.accounts.AccountHasTransactionsException;
import com.rslima.ricash.ledgers.envelopes.EnvelopeHasTransactionsException;

import com.toedter.spring.hateoas.jsonapi.JsonApiError;
import com.toedter.spring.hateoas.jsonapi.JsonApiErrors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Single JSON:API error dialect for every controller: domain not-found
 * exceptions map to 404, has-transactions conflicts to 409, bad input to 400,
 * and everything unexpected to a logged 500 with a generic detail.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<JsonApiErrors> handleEntityNotFound(EntityNotFoundException ex) {
        return errors(NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({AccountHasTransactionsException.class, EnvelopeHasTransactionsException.class})
    public ResponseEntity<JsonApiErrors> handleConflict(RuntimeException ex) {
        return errors(CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<JsonApiErrors> handleIllegalArgument(IllegalArgumentException ex) {
        return errors(BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<JsonApiErrors> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return errors(BAD_REQUEST, "Malformed request body");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<JsonApiErrors> handleValidation(MethodArgumentNotValidException ex) {
        var jsonApiErrors = JsonApiErrors.create();
        ex.getBindingResult().getFieldErrors().forEach(fieldError -> jsonApiErrors.withError(
                JsonApiError.create()
                        .withStatus(Integer.toString(BAD_REQUEST.value()))
                        .withTitle(BAD_REQUEST.getReasonPhrase())
                        .withDetail(fieldError.getField() + " " + fieldError.getDefaultMessage())
                        .withSourcePointer("/data/attributes/" + fieldError.getField())));
        ex.getBindingResult().getGlobalErrors().forEach(globalError -> jsonApiErrors.withError(
                JsonApiError.create()
                        .withStatus(Integer.toString(BAD_REQUEST.value()))
                        .withTitle(BAD_REQUEST.getReasonPhrase())
                        .withDetail(globalError.getDefaultMessage())));
        return ResponseEntity.status(BAD_REQUEST)
                .contentType(MediaType.parseMediaType(JSON_API_VALUE))
                .body(jsonApiErrors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<JsonApiErrors> handleUnexpected(Exception ex) {
        // Framework exceptions (405, 406, 415, ...) carry their own status; keep it.
        if (ex instanceof ErrorResponse errorResponse) {
            var status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return errors(status, status.getReasonPhrase());
        }
        log.error("Unhandled exception", ex);
        return errors(INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<JsonApiErrors> errors(HttpStatus status, String... details) {
        var jsonApiErrors = JsonApiErrors.create();
        for (String detail : details.length > 0 ? List.of(details) : List.of(status.getReasonPhrase())) {
            jsonApiErrors.withError(
                    JsonApiError.create()
                            .withStatus(Integer.toString(status.value()))
                            .withTitle(status.getReasonPhrase())
                            .withDetail(detail));
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType(JSON_API_VALUE))
                .body(jsonApiErrors);
    }
}
