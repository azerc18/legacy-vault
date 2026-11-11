package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminPaymentResponse;
import com.ltld.app.legacyvault.dto.adminoverviewdto.GrantVipRequest;
import com.ltld.app.legacyvault.dto.adminoverviewdto.RefundPaymentRequest;
import com.ltld.app.legacyvault.dto.adminoverviewdto.VipGrantResponse;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminBillingService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

/** Việc 3: Admin tặng VIP thủ công và hoàn tiền giao dịch. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN_MANAGE')")
public class AdminBillingController {

    private final AdminBillingService adminBillingService;

    @PostMapping("/users/{userId}/vip-grant")
    public ResponseEntity<ApiResponse<VipGrantResponse>> grantVip(Principal principal, @PathVariable UUID userId,
                                                                  @Valid @RequestBody GrantVipRequest request) {
        UUID adminId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("VIP granted",
                adminBillingService.grantVip(adminId, userId, request)));
    }

    @PostMapping("/payments/{transactionId}/refund")
    public ResponseEntity<ApiResponse<AdminPaymentResponse>> refund(Principal principal, @PathVariable UUID transactionId,
                                                                    @Valid @RequestBody RefundPaymentRequest request) {
        UUID adminId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Payment refunded",
                adminBillingService.refund(adminId, transactionId, request)));
    }
}
