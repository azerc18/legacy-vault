package com.ltld.app.legacyvault.dto.paymentdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * T1: gói hiện tại của người dùng (GET /api/subscriptions/me).
 *
 * <p>Quy ước:
 * <ul>
 *   <li>Đang có VIP còn hạn: {@code planCode} = gói VIP, {@code status} = ACTIVE, {@code vip} = true,
 *       {@code expiresAt} = ngày hết hạn của gói đó.</li>
 *   <li>Không có VIP còn hạn: {@code planCode} = FREE, {@code vip} = false và mọi hạn mức là của FREE.
 *       {@code status}, {@code startedAt}, {@code expiresAt} lấy từ lần đăng ký gần nhất (nếu có), nên
 *       <b>EXPIRED + FREE</b> nghĩa là "đang dùng FREE vì gói VIP đã hết hạn vào {@code expiresAt}".
 *       Có thể gặp status ACTIVE (hoặc CANCELLED) với expiresAt đã qua khi job hết hạn chưa chạy:
 *       FE dựa vào {@code vip} = false và {@code expiresAt} để hiển thị "đã hết hạn".</li>
 *   <li>Chưa từng đăng ký: planCode = FREE, status = ACTIVE, startedAt/expiresAt = null.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionMeResponse {
    private String planCode;
    private String planName;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime expiresAt;
    private int maxVaults;
    private int maxBeneficiariesPerVault;
    private long maxStorageBytes;
    private boolean legacyMessageEnabled;
    private boolean vip;
    private List<SubscriptionPlanDto> availablePlans;
}
