package com.ltld.app.legacyvault.dto.paymentdto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * T2: dữ liệu để vẽ hoá đơn mô phỏng (PDF) (chỉ giao dịch SUCCESS của chính Owner).
 * Không chứa thông tin thẻ hay bí mật VNPay.
 */
public record PaymentReceiptData(
        String receiptNo,
        UUID transactionId,
        String customerName,
        String customerEmail,
        String planCode,
        String planName,
        Integer durationDays,
        long amountVnd,
        String vnpTransactionNo,
        String vnpBankCode,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime issuedAt,
        long invoiceNumber,
        String paymentMethod) {
}
