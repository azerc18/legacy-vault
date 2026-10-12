package com.ltld.app.legacyvault.exception;

// FR-09 (2a): tệp giấy chứng tử rỗng, quá lớn hoặc sai định dạng
public class InvalidDeathCertificateException extends RuntimeException {
    public InvalidDeathCertificateException(String message) {
        super(message);
    }
}
