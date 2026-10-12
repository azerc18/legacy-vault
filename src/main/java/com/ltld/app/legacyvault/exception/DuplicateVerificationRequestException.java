package com.ltld.app.legacyvault.exception;

// FR-09 (4a): Vault đã có yêu cầu xác minh pháp lý đang mở
public class DuplicateVerificationRequestException extends RuntimeException {
    public DuplicateVerificationRequestException(String message) {
        super(message);
    }
}
