package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class BeneficiaryException extends RuntimeException {
    private final HttpStatus status;

    public BeneficiaryException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}