package com.ltld.app.legacyvault.exception;

// Kế thừa RuntimeException để Spring Boot có thể tự động bắt lỗi
public class BeneficiaryException extends RuntimeException {
    public BeneficiaryException(String message) {
        super(message);
    }
}