package com.ltld.app.legacyvault.exception;

// FR-08: Exception thrown when uploaded death certificate is invalid or unsupported
public class InvalidVerificationDocumentException extends RuntimeException {
    public InvalidVerificationDocumentException(String message) {
        super(message);
    }
}
