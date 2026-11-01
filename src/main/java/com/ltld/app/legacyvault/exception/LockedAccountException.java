package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class LockedAccountException extends BusinessException {
    public LockedAccountException() {
        super("Your account is locked. Try again later.", HttpStatus.FORBIDDEN,ErrorCode.ACCOUNT_LOCKED);
    }
}
