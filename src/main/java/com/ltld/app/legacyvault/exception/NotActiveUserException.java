package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class NotActiveUserException extends BusinessException {
    public NotActiveUserException() {
        super("Your account is not active. Please verify OTP before logging in.", HttpStatus.FORBIDDEN);
    }
}
