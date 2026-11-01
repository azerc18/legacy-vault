package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialException extends BusinessException  {
    public InvalidCredentialException() {
        super("Invalid email or password", HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS);
    }
}
