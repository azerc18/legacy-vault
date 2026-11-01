package com.ltld.app.legacyvault.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class DmsException extends RuntimeException {
    public static final String BENEFICIARY_CONFLICT = "BENEFICIARY_CONFLICT";
    public static final String CHECKIN_TOKEN_INVALID = "CHECKIN_TOKEN_INVALID";
    public static final String CONFIRMER_TOKEN_INVALID = "CONFIRMER_TOKEN_INVALID";

    private final HttpStatus status;
    private final String errorCode;

    public DmsException(String message, HttpStatus status) {
        this(message, status, null);
    }

    public DmsException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static DmsException beneficiaryConflict(String message) {
        return new DmsException(message, HttpStatus.CONFLICT, BENEFICIARY_CONFLICT);
    }

    public static DmsException checkinTokenInvalid(String message) {
        return new DmsException(message, HttpStatus.BAD_REQUEST, CHECKIN_TOKEN_INVALID);
    }

    public static DmsException confirmerTokenInvalid(String message) {
        return new DmsException(message, HttpStatus.BAD_REQUEST, CONFIRMER_TOKEN_INVALID);
    }

    public DmsException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
