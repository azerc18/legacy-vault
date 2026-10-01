package com.ltld.app.legacyvault.exception;

public class LockedAccountException extends RuntimeException {
    public LockedAccountException() {
        super("Your account is locked. Try again later.");
    }
}
