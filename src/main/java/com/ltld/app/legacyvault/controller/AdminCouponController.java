package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.coupondto.AdminCouponResponse;
import com.ltld.app.legacyvault.dto.coupondto.CreateCouponRequest;
import com.ltld.app.legacyvault.dto.coupondto.UpdateCouponRequest;
import com.ltld.app.legacyvault.service.paymentservice.CouponService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/** Việc 4: admin tạo và bật / tắt mã giảm giá. */
@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN_MANAGE')")
public class AdminCouponController {

    private final CouponService couponService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminCouponResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(couponService.list()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminCouponResponse>> create(Principal principal,
                                                                   @Valid @RequestBody CreateCouponRequest request) {
        UUID adminId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Code created", couponService.create(adminId, request)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminCouponResponse>> update(Principal principal, @PathVariable UUID id,
                                                                   @Valid @RequestBody UpdateCouponRequest request) {
        UUID adminId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Code updated", couponService.setActive(adminId, id, request.active())));
    }
}
