package com.ltld.app.legacyvault.utility;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ltld.app.legacyvault.exception.ErrorCode;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private final boolean success;
    private final String message;
    private final T data;
    private final LocalDateTime timestamp;
    private final ErrorCode errorCode;

    public ApiResponse(boolean success, ErrorCode errorCode, String message, T data) {
        this.success = success;
        this.message = message;
        this.errorCode=errorCode;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    public ApiResponse(boolean success, String message, T data) {
        this(success, null, message, data);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode, message, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message, T data) {
        return new ApiResponse<>(false, errorCode, message, data);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null,"Success", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true,null, message, data);
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(true,null, message, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false,null, message, null);
    }

    public static <T> ApiResponse<T> error(String message, T data) {
        return new ApiResponse<>(false,null, message, data);
    }
}
