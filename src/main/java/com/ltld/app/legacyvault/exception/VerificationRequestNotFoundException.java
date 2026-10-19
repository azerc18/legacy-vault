package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class VerificationRequestNotFoundException extends BusinessException {
    public VerificationRequestNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}