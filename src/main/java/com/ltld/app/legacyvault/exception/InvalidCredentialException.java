package com.ltld.app.legacyvault.exception;

public class InvalidCredentialException extends RuntimeException {
    public InvalidCredentialException() {
        super("Invalid email or password");
    }
}
