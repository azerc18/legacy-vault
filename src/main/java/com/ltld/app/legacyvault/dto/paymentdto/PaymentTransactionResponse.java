package com.ltld.app.legacyvault.dto.paymentdto;

import com.ltld.app.legacyvault.entity.PaymentTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransactionResponse {
    private UUID id;
    private String planCode;
    private String planName;
    private long amountVnd;
    private String status;
    private String vnpTransactionNo;
    private String vnpResponseCode;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;

    public static PaymentTransactionResponse from(PaymentTransaction tx) {
        return PaymentTransactionResponse.builder()
                .id(tx.getId())
                .planCode(tx.getPlan() != null ? tx.getPlan().getCode() : null)
                .planName(tx.getPlan() != null ? tx.getPlan().getName() : null)
                .amountVnd(tx.getAmountVnd())
                .status(tx.getStatus() != null ? tx.getStatus().name() : null)
                .vnpTransactionNo(tx.getVnpTransactionNo())
                .vnpResponseCode(tx.getVnpResponseCode())
                .createdAt(tx.getCreatedAt())
                .paidAt(tx.getPaidAt())
                .build();
    }
}
