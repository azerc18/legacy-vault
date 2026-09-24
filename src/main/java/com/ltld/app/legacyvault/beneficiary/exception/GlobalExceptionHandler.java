package com.ltld.app.legacyvault.beneficiary.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

// @RestControllerAdvice: Đứng gác ở vòng ngoài, tự động bắt tất cả các lỗi văng ra từ Controller
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Chỉ định hàm này sẽ xử lý riêng cho BeneficiaryException
    @ExceptionHandler(BeneficiaryException.class)
    public ResponseEntity<Map<String, String>> handleBeneficiaryException(BeneficiaryException ex) {
        Map<String, String> response = new HashMap<>();
        // Đưa câu thông báo lỗi (ví dụ: "Mã xác thực không chính xác") vào biến "error"
        response.put("error", ex.getMessage());

        // Trả về HTTP Status 400 (Bad Request) kèm theo nội dung JSON
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}