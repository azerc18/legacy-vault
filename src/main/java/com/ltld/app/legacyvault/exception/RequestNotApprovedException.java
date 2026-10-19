package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class RequestNotApprovedException extends BusinessException {
    public RequestNotApprovedException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}