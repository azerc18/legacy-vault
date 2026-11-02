package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class InvalidDateRangeException extends BusinessException {
    public InvalidDateRangeException(String message) {
        super(message, HttpStatus.BAD_REQUEST,ErrorCode.INVALID_DATE_RANGE);
    }
}
