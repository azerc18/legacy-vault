package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.dto.paymentdto.PaymentReceiptData;
import com.ltld.app.legacyvault.entity.PaymentTransaction;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * T2: lấy dữ liệu biên lai thanh toán. Chỉ Owner sở hữu giao dịch mới xem được, và chỉ khi giao dịch SUCCESS.
 * Giao dịch của người khác trả NOT_FOUND (không để lộ là id có tồn tại).
 */
@Service
@RequiredArgsConstructor
public class PaymentReceiptService {

    // Hiện chỉ có VNPay; khi có thêm cổng khác thì suy ra từ provider của giao dịch
    private static final String PAYMENT_METHOD_VNPAY = "VNPay";
    private static final DateTimeFormatter RECEIPT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PaymentTransactionRepository paymentTransactionRepository;

    @Transactional(readOnly = true)
    public PaymentReceiptData getReceiptData(UUID transactionId, UUID userId) {
        PaymentTransaction tx = paymentTransactionRepository.findById(transactionId)
                .filter(t -> t.getUser() != null && t.getUser().getId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Payment not found."));

        if (tx.getStatus() != PaymentStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.CONFLICT, "A receipt is only available for successful payments.");
        }

        User user = tx.getUser();
        SubscriptionPlan plan = tx.getPlan();
        // Ngày lập = lúc thanh toán (không dùng now()) để mỗi lần tải lại hoá đơn vẫn y hệt
        LocalDateTime issuedAt = tx.getPaidAt() != null ? tx.getPaidAt() : tx.getCreatedAt();
        // Số hoá đơn mô phỏng = thứ tự của giao dịch trong các giao dịch SUCCESS (ổn định, không cần cột mới).
        // Giới hạn đã biết (chấp nhận được vì hoá đơn chỉ mô phỏng): hai giao dịch có cùng paidAt sẽ trùng số,
        // và nếu sau này có hoàn tiền/xoá giao dịch SUCCESS thì số của các hoá đơn sau đó bị xê dịch.
        // Cần số duy nhất thật thì thay bằng bảng/sequence; khi dữ liệu lớn thì thêm chỉ mục (status, paid_at).
        long invoiceNumber = Math.max(1L, paymentTransactionRepository
                .countByStatusAndPaidAtLessThanEqual(PaymentStatus.SUCCESS, issuedAt));
        return new PaymentReceiptData(
                receiptNo(tx),
                tx.getId(),
                user.getFullName(),
                user.getEmail(),
                plan != null ? plan.getCode() : null,
                plan != null ? plan.getName() : null,
                plan != null ? plan.getDurationDays() : null,
                tx.getAmountVnd(),
                tx.getVnpTransactionNo(),
                tx.getVnpBankCode(),
                tx.getCreatedAt(),
                tx.getPaidAt(),
                issuedAt,
                invoiceNumber,
                PAYMENT_METHOD_VNPAY);
    }

    /** Số biên lai ổn định cho mỗi giao dịch: LV-{ngày thanh toán}-{8 ký tự đầu của id}, vd. LV-20261107-1A2B3C4D. */
    static String receiptNo(PaymentTransaction tx) {
        LocalDateTime date = tx.getPaidAt() != null ? tx.getPaidAt() : tx.getCreatedAt();
        String datePart = date != null ? date.format(RECEIPT_DATE) : "00000000";
        String idPart = tx.getId().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "LV-" + datePart + "-" + idPart;
    }
}
