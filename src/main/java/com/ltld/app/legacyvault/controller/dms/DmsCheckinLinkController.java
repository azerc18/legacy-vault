package com.ltld.app.legacyvault.controller.dms;

import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.service.dmsservice.DmsOneClickCheckinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * F2: Endpoint công khai (permitAll) xử lý nút "Tôi vẫn an toàn / I'm OK" từ email nhắc check-in DMS.
 * Tách riêng khỏi DmsController (@PreAuthorize("hasRole('OWNER')")) để không cần đăng nhập khi bấm từ email.
 */
@Slf4j
@RestController
@RequestMapping("/api/dms")
@RequiredArgsConstructor
public class DmsCheckinLinkController {

    private final DmsOneClickCheckinService dmsOneClickCheckinService;

    // Người dùng bấm từ email (trình duyệt), nên lỗi cũng redirect về trang FE thay vì trả JSON.
    // Bắt lỗi ở đây, NGOÀI transaction của service: proxy đã rollback xong rồi mới chuyển hướng.
    // required = false: thiếu token -> service ném CHECKIN_TOKEN_INVALID -> cũng redirect.
    @GetMapping("/checkin-link")
    public ResponseEntity<Void> checkInByEmailLink(@RequestParam(value = "token", required = false) String token) {
        String redirectUrl;
        try {
            redirectUrl = dmsOneClickCheckinService.checkInAndBuildRedirectUrl(token);
        } catch (BusinessException e) {
            redirectUrl = dmsOneClickCheckinService.buildErrorRedirectUrl(e.getErrorCode());
        } catch (RuntimeException e) {
            log.error("F2: one-click DMS check-in failed unexpectedly", e);
            redirectUrl = dmsOneClickCheckinService.buildErrorRedirectUrl(ErrorCode.INTERNAL_ERROR);
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(redirectUrl).toString())
                .build();
    }
}

