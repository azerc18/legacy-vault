package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.paymentdto.SubscriptionMeResponse;
import com.ltld.app.legacyvault.service.paymentservice.PaymentService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SubscriptionMeResponse>> getMySubscription(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription retrieved successfully",
                paymentService.getMySubscription(userId)));
    }
}

