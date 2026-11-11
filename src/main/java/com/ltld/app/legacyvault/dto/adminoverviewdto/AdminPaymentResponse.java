package com.ltld.app.legacyvault.dto.adminoverviewdto;

import com.ltld.app.legacyvault.enums.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** A2: một dòng giao dịch trên trang Thanh toán của Admin. */
@Getter
@Builder
public class AdminPaymentResponse {
    private UUID id;
    private UUID userId;
    private String userEmail;
    private String userFullName;
    private String planCode;
    private String planName;
    private long amountVnd;
    private PaymentStatus status;
    // Hiện chỉ có VNPay; khi có cột provider (PR của Bách) sẽ đọc từ giao dịch
    private String provider;
    private String providerTransactionNo;
    private String bankCode;
    private String responseCode;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    // Việc 3: thời điểm và lý do admin hoàn tiền (null nếu chưa hoàn)
    private LocalDateTime refundedAt;
    private String refundReason;
    // Gói đang dùng hiện tại của user (FREE nếu không có gói trả phí còn hạn)
    private String currentPlanCode;
}
