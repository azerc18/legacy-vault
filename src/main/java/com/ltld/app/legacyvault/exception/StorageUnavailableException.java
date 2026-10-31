package com.ltld.app.legacyvault.exception;

/** H1: không kết nối được object storage (S3 / MinIO / R2) -> 503 STORAGE_UNAVAILABLE. */
public class StorageUnavailableException extends BusinessException {

    public StorageUnavailableException(String message, Throwable cause) {
        super(ErrorCode.STORAGE_UNAVAILABLE, message);
        initCause(cause);
    }
}
