package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class InvalidVerificationDocumentException extends BusinessException {
    public InvalidVerificationDocumentException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
