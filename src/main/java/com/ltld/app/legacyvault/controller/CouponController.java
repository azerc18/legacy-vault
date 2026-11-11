package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.coupondto.CouponPreviewResponse;
import com.ltld.app.legacyvault.service.paymentservice.CouponService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

/** Việc 4: Owner kiểm tra mã giảm giá ở trang Bảng giá trước khi thanh toán. */
@RestController
@RequestMapping("/api/payments/coupons")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<CouponPreviewResponse>> preview(Principal principal, @RequestParam String code) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(couponService.preview(userId, code)));
    }
}
