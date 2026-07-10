package com.rslima.ricash.exceptions;

/**
 * Base for business-rule conflicts rendered as HTTP 409 by the
 * GlobalExceptionHandler.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
