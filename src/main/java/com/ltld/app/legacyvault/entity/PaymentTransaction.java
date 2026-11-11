package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.PaymentProvider;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;

import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

// T1: Lịch sử giao dịch thanh toán VNPay
@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(name = "idx_payment_tx_user_created", columnList = "user_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "amount_vnd", nullable = false)
    private long amountVnd;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentProvider provider = PaymentProvider.VNPAY;

    // PayPal: order id, dùng để tìm lại giao dịch khi người mua quay về
    @Column(name = "gateway_txn_ref", length = 100)
    private String gatewayTxnRef;

    // PayPal không nhận VND: số USD đã tính lúc checkout, để đối chiếu khi capture và in hóa đơn
    @Column(name = "amount_usd_cents")
    private Long amountUsdCents;

    @Column(name = "vnp_transaction_no", length = 100)
    private String vnpTransactionNo;

    @Column(name = "vnp_response_code", length = 20)
    private String vnpResponseCode;

    @Column(name = "vnp_bank_code", length = 50)
    private String vnpBankCode;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** Việc 3: lúc admin hoàn tiền, lý do và mã hoàn tiền của cổng (null khi ghi nhận hoàn tay). */
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "refund_reason", length = 255)
    private String refundReason;

    @Column(name = "refund_ref", length = 100)
    private String refundRef;

    // Việc 4: mã giảm giá đã áp dụng; amountVnd là số tiền sau giảm
    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @Column(name = "discount_vnd")
    private Long discountVnd;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

