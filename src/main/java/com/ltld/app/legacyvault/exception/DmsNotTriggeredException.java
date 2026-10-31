package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class DmsNotTriggeredException extends BusinessException {
    public DmsNotTriggeredException(String message) {
        super(message, HttpStatus.CONFLICT,ErrorCode.DMS_NOT_TRIGGERED);
    }
}
