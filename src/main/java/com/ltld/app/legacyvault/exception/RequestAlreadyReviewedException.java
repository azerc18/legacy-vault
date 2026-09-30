package com.ltld.app.legacyvault.exception;

public class RequestAlreadyReviewedException extends RuntimeException {
    public RequestAlreadyReviewedException(String message) {
        super(message);
    }
}