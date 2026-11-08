package com.ltld.app.legacyvault.dto.adminoverviewdto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A1: số liệu trang tổng quan Admin + danh sách việc cần xử lý. Chỉ đọc. */
@Getter
@Builder
public class AdminStatsResponse {
    // Đếm theo trạng thái; trạng thái chưa có bản ghi nào vẫn có mặt với giá trị 0
    private Map<String, Long> usersByStatus;
    private Map<String, Long> vaultsByStatus;

    private long openAlerts;
    private long openHighAlerts;

    private long dmsPendingConfirmation;
    private long dmsTriggered;
    private long executorRescueNeeded;

    // Doanh thu tháng hiện tại (theo giờ server), chỉ tính giao dịch SUCCESS theo paidAt
    private long revenueThisMonthVnd;
    private long successfulPaymentsThisMonth;
    // Số user đang có gói VIP còn hạn
    private long vipUsers;

    private List<AlertItem> highAlerts;
    private List<VaultDeadlineItem> claimDeadlines;
    private List<PendingPaymentItem> stalePendingPayments;

    @Getter
    @Builder
    public static class AlertItem {
        private UUID id;
        private String rule;
        private String subject;
        private String message;
        private int eventCount;
        private Instant lastSeenAt;
    }

    @Getter
    @Builder
    public static class VaultDeadlineItem {
        private UUID id;
        private String name;
        private UUID ownerId;
        private String ownerEmail;
        private LocalDateTime claimDeadlineAt;
    }

    @Getter
    @Builder
    public static class PendingPaymentItem {
        private UUID id;
        private UUID userId;
        private String userEmail;
        private String planCode;
        private long amountVnd;
        private LocalDateTime createdAt;
    }
}
