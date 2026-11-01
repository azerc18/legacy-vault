package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.paymentdto.CheckoutRequest;
import com.ltld.app.legacyvault.dto.paymentdto.CheckoutResponse;
import com.ltld.app.legacyvault.dto.paymentdto.PaymentTransactionResponse;
import com.ltld.app.legacyvault.service.paymentservice.PaymentService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * T1: API thanh toán VNPay và lịch sử giao dịch.
 */
@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            Principal principal,
            @Valid @RequestBody CheckoutRequest request,
            HttpServletRequest servletRequest) {
        UUID userId = UUID.fromString(principal.getName());
        String ipAddr = extractClientIp(servletRequest);
        CheckoutResponse response = paymentService.checkout(userId, request, ipAddr);
        return ResponseEntity.ok(ApiResponse.success("Payment URL created successfully", response));
    }

    @GetMapping("/vnpay-return")
    public ResponseEntity<Void> vnpayReturn(@RequestParam Map<String, String> params) {
        String redirectUrl = paymentService.handleVnpayReturn(params);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(redirectUrl).toString())
                .build();
    }

    @GetMapping("/vnpay-ipn")
    public ResponseEntity<Map<String, String>> vnpayIpn(@RequestParam Map<String, String> params) {
        // VNPay chỉ hiểu {RspCode, Message} với HTTP 200: lỗi bất ngờ trả 99 (service đã rollback), không để lỗi 4xx/5xx lọt ra
        try {
            return ResponseEntity.ok(paymentService.handleVnpayIpn(params));
        } catch (Exception ex) {
            log.error("T1: unexpected error while handling VNPay IPN", ex);
            return ResponseEntity.ok(Map.of("RspCode", "99", "Message", "Unknown error"));
        }
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<List<PaymentTransactionResponse>>> getMyPayments(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(
                "Payment history retrieved successfully",
                paymentService.getMyPayments(userId)));
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return (remote == null || remote.isBlank()) ? "127.0.0.1" : remote;
    }
}

