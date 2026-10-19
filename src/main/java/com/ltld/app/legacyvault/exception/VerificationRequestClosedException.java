package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class VerificationRequestClosedException extends BusinessException {
    public VerificationRequestClosedException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
