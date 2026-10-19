package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class InvalidSignCodeException extends BusinessException {
    public InvalidSignCodeException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
