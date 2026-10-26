package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

/** T-3: Verifier chưa làm đủ bước View → Check trước khi Check/Approve/Reject (409). */
public class VerificationStepRequiredException extends BusinessException {
    public VerificationStepRequiredException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
