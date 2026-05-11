package com.smartamenities.backend.exception;

/**
 * Raised when a requested domain resource cannot be resolved.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
