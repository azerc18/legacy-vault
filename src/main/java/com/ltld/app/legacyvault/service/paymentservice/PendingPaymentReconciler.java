package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.enums.PaymentStatus;
import com.ltld.app.legacyvault.repository.PaymentTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Định kỳ đóng các đơn PENDING quá hạn (người mua bỏ ngang). Trước khi đóng, hỏi lại cổng thanh toán
 * (VNPay querydr, PayPal GET order) để không bỏ sót đơn đã trừ tiền mà IPN / lần quay về bị lỡ.
 *
 * <p>Mỗi đơn chạy trong transaction riêng của {@link PaymentService#reconcilePending}, có khóa dòng nên không
 * đụng IPN về cùng lúc. VNPay huỷ phiên sau 15 phút, nên mặc định chỉ đụng tới đơn cũ hơn 30 phút.
 *
 * <p>Đơn bị thay thế (EXPIRED vì checkout lại) chưa thu tiền trong {@code give-up-after} gần nhất cũng được hỏi lại
 * (chỉ để kích hoạt nếu cổng báo đã trả), vì checkout lại sẽ đóng ngay đơn cũ mà người mua có thể đã trả nhưng IPN
 * bị lỡ. Với PayPal chỉ đọc kết quả đã capture, không bao giờ capture đơn đã bị thay thế.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.reconcile.enabled", havingValue = "true", matchIfMissing = true)
public class PendingPaymentReconciler {

    /** Lý do đóng đơn khi người mua checkout lại; xem PaymentServiceImpl.checkout. */
    static final String SUPERSEDED = "SUPERSEDED";

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentService paymentService;
    private final Duration pendingTimeout;
    private final Duration giveUpAfter;
    private final int batchSize;

    public PendingPaymentReconciler(PaymentTransactionRepository paymentTransactionRepository,
                                    PaymentService paymentService,
                                    @Value("${app.payment.reconcile.pending-timeout:PT30M}") Duration pendingTimeout,
                                    @Value("${app.payment.reconcile.give-up-after:PT24H}") Duration giveUpAfter,
                                    @Value("${app.payment.reconcile.batch-size:50}") int batchSize) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentService = paymentService;
        this.pendingTimeout = pendingTimeout;
        this.giveUpAfter = giveUpAfter;
        this.batchSize = batchSize;
    }

    @Scheduled(initialDelayString = "${app.payment.reconcile.initial-delay:PT2M}",
            fixedDelayString = "${app.payment.reconcile.interval:PT10M}")
    public void runScheduled() {
        reconcile(LocalDateTime.now());
    }

    public Map<ReconcileOutcome, Integer> reconcile(LocalDateTime now) {
        Map<ReconcileOutcome, Integer> counts = new EnumMap<>(ReconcileOutcome.class);
        // Đơn đã đóng trước: hỏi lại trước, để đơn vừa đóng ở lượt này đợi tới lượt sau mới hỏi lại
        // Chỉ đơn bị thay thế khi checkout lại: các đơn khác đã EXPIRED vì cổng trả lời chắc chắn là chưa trả
        // (91, mã thất bại...) hoặc đã quá give-up-after, hỏi lại chỉ tốn lượt gọi cổng (review #251 m5).
        List<UUID> expired = paymentTransactionRepository.findUnpaidIdsByStatusAndReasonSince(
                PaymentStatus.EXPIRED, SUPERSEDED, now.minus(giveUpAfter), PageRequest.of(0, batchSize));
        List<UUID> pending = paymentTransactionRepository.findIdsByStatusAndCreatedAtBefore(
                PaymentStatus.PENDING, now.minus(pendingTimeout), PageRequest.of(0, batchSize));
        run(expired, now, counts);
        run(pending, now, counts);
        if (!expired.isEmpty() || !pending.isEmpty()) {
            log.info("Reconcile: checked {} stale pending and {} recent expired payment(s): {}",
                    pending.size(), expired.size(), counts);
        }
        return counts;
    }

    private void run(List<UUID> ids, LocalDateTime now, Map<ReconcileOutcome, Integer> counts) {
        for (UUID id : ids) {
            ReconcileOutcome outcome;
            try {
                outcome = paymentService.reconcilePending(id, now, giveUpAfter);
            } catch (RuntimeException ex) {
                log.warn("Reconcile: payment {} failed: {}", id, ex.getMessage());
                outcome = ReconcileOutcome.STILL_PENDING;
            }
            counts.merge(outcome, 1, Integer::sum);
        }
    }
}
