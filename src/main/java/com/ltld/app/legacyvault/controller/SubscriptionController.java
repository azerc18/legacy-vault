package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.paymentdto.PlanUsageResponse;
import com.ltld.app.legacyvault.dto.paymentdto.SubscriptionMeResponse;
import com.ltld.app.legacyvault.service.paymentservice.PaymentService;
import com.ltld.app.legacyvault.service.paymentservice.SubscriptionRenewalService;
import com.ltld.app.legacyvault.service.paymentservice.PlanUsageService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

/**
 * T1: API tra cứu gói cước hiện tại và hạn mức của Owner.
 */
@RestController
@RequestMapping("/api/subscriptions")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class SubscriptionController {

    private final PaymentService paymentService;
    private final SubscriptionRenewalService renewalService;
    private final PlanUsageService planUsageService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SubscriptionMeResponse>> getMySubscription(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription retrieved successfully",
                paymentService.getMySubscription(userId)));
    }

    // Việc 6: hủy gia hạn cuối kỳ (giữ VIP tới hết hạn, không hoàn tiền)
    @PostMapping("/me/cancel")
    public ResponseEntity<ApiResponse<SubscriptionMeResponse>> cancelAtPeriodEnd(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(
                "Renewal turned off", renewalService.cancelAtPeriodEnd(userId)));
    }

    // Việc 6: bật lại gia hạn trước khi gói hết hạn
    @PostMapping("/me/resume")
    public ResponseEntity<ApiResponse<SubscriptionMeResponse>> resume(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(
                "Renewal turned back on", renewalService.resume(userId)));
    }

    /** Việc 2: đã dùng bao nhiêu so với hạn mức gói, và mục nào đang vượt (sau khi VIP hết hạn). */
    @GetMapping("/me/usage")
    public ResponseEntity<ApiResponse<PlanUsageResponse>> getMyUsage(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(
                "Plan usage retrieved successfully",
                planUsageService.getUsage(userId)));
    }
}
