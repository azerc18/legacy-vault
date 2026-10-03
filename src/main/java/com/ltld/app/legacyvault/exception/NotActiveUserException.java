package com.ltld.app.legacyvault.exception;

public class NotActiveUserException extends RuntimeException {
    public NotActiveUserException() {
        super("Your account is not active. Please verify OTP before logging in.");
    }
}
