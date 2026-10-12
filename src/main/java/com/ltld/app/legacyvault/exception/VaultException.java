package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class VaultException extends RuntimeException {
    private final HttpStatus status;

    public VaultException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
    }

    public VaultException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
