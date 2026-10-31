package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

// T1: Lỗi thanh toán VNPay và gói thuê bao (mang errorCode chuẩn theo mục 8).
public class PaymentException extends BusinessException {

    public static final String SIGNATURE_INVALID = "PAYMENT_SIGNATURE_INVALID";
    public static final String AMOUNT_MISMATCH = "PAYMENT_AMOUNT_MISMATCH";

    private final String errorCode;

    public PaymentException(String message, HttpStatus status) {
        this(message, status, null);
    }

    public PaymentException(String message, HttpStatus status, String errorCode) {
        super(message, status);
        this.errorCode = errorCode;
    }

    public static PaymentException signatureInvalid() {
        return new PaymentException("Invalid VNPay signature.", HttpStatus.BAD_REQUEST, SIGNATURE_INVALID);
    }

    public static PaymentException amountMismatch() {
        return new PaymentException("Payment amount does not match the transaction.", HttpStatus.BAD_REQUEST, AMOUNT_MISMATCH);
    }

    public String getErrorCode() {
        return errorCode;
    }
}

