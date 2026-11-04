package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.dmsdto.OneClickCheckinRequest;
import com.ltld.app.legacyvault.dto.dmsdto.OneClickCheckinResponse;
import com.ltld.app.legacyvault.exception.DmsCheckinLinkException;
import com.ltld.app.legacyvault.service.dmsservice.DmsOneClickCheckinService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * F2: check-in một chạm từ email nhắc, không cần đăng nhập (SecurityConfig permitAll POST /api/dms/checkin-link).
 * Tách khỏi DmsController (@PreAuthorize OWNER). Bảo vệ bằng token check-in ký riêng (audience riêng, jti dùng 1 lần).
 */
@RestController
@RequestMapping("/api/dms")
@RequiredArgsConstructor
public class DmsCheckinLinkController {

    private final DmsOneClickCheckinService dmsOneClickCheckinService;

    // POST do Owner bấm trên trang FE /dms/checkin; link GET trong email chỉ mở trang, không đổi trạng thái
    // (trình quét link của hộp thư không check-in hộ được).
    @PostMapping("/checkin-link")
    public ResponseEntity<ApiResponse<OneClickCheckinResponse>> checkInByEmailLink(
            @Valid @RequestBody OneClickCheckinRequest request) {
        return ResponseEntity.ok(ApiResponse.success(dmsOneClickCheckinService.checkInWithToken(request.getToken())));
    }

    // m5: như GlobalExceptionHandler.handleBusiness, thêm data.reason để FE chọn câu trong namespace checkin.
    @ExceptionHandler(DmsCheckinLinkException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleCheckinLink(DmsCheckinLinkException e) {
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponse.error(e.getErrorCode(), e.getMessage(), Map.of("reason", e.getReason().name())));
    }
}
