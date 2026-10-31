package com.ltld.app.legacyvault.controller.dms;

import com.ltld.app.legacyvault.service.dmsservice.DmsOneClickCheckinService;
import lombok.RequiredArgsConstructor;
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
@RestController
@RequestMapping("/api/dms")
@RequiredArgsConstructor
public class DmsCheckinLinkController {

    private final DmsOneClickCheckinService dmsOneClickCheckinService;

    @GetMapping("/checkin-link")
    public ResponseEntity<Void> checkInByEmailLink(@RequestParam("token") String token) {
        String redirectUrl = dmsOneClickCheckinService.checkInAndBuildRedirectUrl(token);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(redirectUrl).toString())
                .build();
    }
}

