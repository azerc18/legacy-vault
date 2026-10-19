package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends BusinessException {
    public InvalidRefreshTokenException() {
        super("Refresh token is invalid or has been revoked.", HttpStatus.UNAUTHORIZED);
    }

    public InvalidRefreshTokenException(String message) {
        super(message,HttpStatus.UNAUTHORIZED);
    }
}
