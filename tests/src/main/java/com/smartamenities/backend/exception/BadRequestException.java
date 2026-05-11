package com.smartamenities.backend.exception;

/**
 * Raised for client-side request validation errors.
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
