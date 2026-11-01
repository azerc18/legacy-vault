package com.ltld.app.legacyvault.exception;

import org.springframework.http.HttpStatus;

public class RequestAlreadyReviewedException extends BusinessException {
    public RequestAlreadyReviewedException(String message) {
        super(message, HttpStatus.CONFLICT,	ErrorCode.REQUEST_ALREADY_REVIEWED
);
    }
}