package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class ConflictOfInterestException extends BusinessException {
    public ConflictOfInterestException(String message) { super(message, HttpStatus.FORBIDDEN,ErrorCode.CONFLICT_OF_INTEREST); }
}