package com.ltld.app.legacyvault.exception;

public class UserAlreadyActiveException extends RuntimeException {
    public UserAlreadyActiveException() {
        super("User already active");
    }
}
