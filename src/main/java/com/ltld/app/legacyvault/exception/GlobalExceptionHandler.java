package com.ltld.app.legacyvault.exception;

import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Mọi lỗi đều trả {success:false, errorCode, message}; status lấy theo ErrorCode
    private static ResponseEntity<ApiResponse<Void>> fail(ErrorCode code, String message) {
        return ResponseEntity.status(code.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.fail(code, message));
    }

    // ---------- Lỗi nghiệp vụ ----------

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        // Dùng e.getStatus() (không phải code.getStatus()) để giữ status nơi ném chọn qua constructor cũ
        return ResponseEntity.status(e.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.fail(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(TooManyOtpRequestsException.class)
    public ResponseEntity<ApiResponse<Map<String, Long>>> handleTooManyOtpRequest(TooManyOtpRequestsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(ErrorCode.TOO_MANY_OTP_REQUESTS, e.getMessage(),
                        Map.of("retryAfterSeconds", e.getRetryAfterSeconds())));
    }

    // ---------- Lỗi request ----------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationErrors(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());   // giữ lỗi đầu tiên của mỗi field
        }
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, "Validation error", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        return fail(ErrorCode.MALFORMED_BODY, "Malformed request body");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return fail(ErrorCode.INVALID_PARAMETER, "Parameter '" + e.getName() + "' has an invalid format");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return fail(ErrorCode.MISSING_PARAMETER, "Missing required parameter '" + e.getParameterName() + "'");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestPart(MissingServletRequestPartException e) {
        return fail(ErrorCode.MISSING_PARAMETER, "Missing required request part: " + e.getRequestPartName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooLarge(MaxUploadSizeExceededException e) {
        return fail(ErrorCode.FILE_TOO_LARGE, "File exceeds the allowed size (10MB).");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException e) {
        return fail(ErrorCode.NOT_FOUND, "Resource not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return fail(ErrorCode.METHOD_NOT_ALLOWED, "Method not allowed");
    }

    // ---------- Phân quyền ----------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return fail(ErrorCode.FORBIDDEN, "Forbidden");
    }

    // ---------- Dữ liệu / đồng thời ----------

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return fail(ErrorCode.CONCURRENT_MODIFICATION, "Request conflict. Please try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("DataIntegrityViolation: {}", e.getMostSpecificCause().getMessage());
        return fail(ErrorCode.DATA_CONFLICT, "The data has changed or is duplicated. Please reload.");
    }

    // ---------- Còn lại ----------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(Exception e) {
        log.error("Unhandled exception", e);
        return fail(ErrorCode.INTERNAL_ERROR, "Internal server error");
    }
}