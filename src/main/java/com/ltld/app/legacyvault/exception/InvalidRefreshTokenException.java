package com.ltld.app.legacyvault.exception;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Refresh token is invalid or has been revoked.");
    }

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
