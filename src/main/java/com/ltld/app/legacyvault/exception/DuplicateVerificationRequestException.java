package com.ltld.app.legacyvault.exception;

// FR-08: Exception thrown when a pending verification request already exists
public class DuplicateVerificationRequestException extends RuntimeException {
    public DuplicateVerificationRequestException(String message) {
        super(message);
    }
}
