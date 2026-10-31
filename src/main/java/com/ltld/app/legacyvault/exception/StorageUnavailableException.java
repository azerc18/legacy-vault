package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

/** H1: không kết nối được object storage (S3 / MinIO / R2). Trả 503 qua handler chung của BusinessException. */
public class StorageUnavailableException extends BusinessException {

    public StorageUnavailableException(String message, Throwable cause) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
        initCause(cause);
    }
}
