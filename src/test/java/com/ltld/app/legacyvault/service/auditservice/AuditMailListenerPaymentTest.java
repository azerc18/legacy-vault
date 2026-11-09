package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.paymentdto.PaymentReceiptData;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.repository.SubscriptionRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.paymentservice.PaymentReceiptPdfService;
import com.ltld.app.legacyvault.service.paymentservice.PaymentReceiptService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditMailListenerPaymentTest {

    @Mock private EmailSender emailSender;
    @Mock private UserRepository userRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PaymentReceiptService receiptService;
    @Mock private PaymentReceiptPdfService pdfService;
    @InjectMocks private AuditMailListener listener;

    private final UUID userId = UUID.randomUUID();
    private final UUID vaultId = UUID.randomUUID();
    private final UUID entityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("an@example.com");
        user.setFullName("An");
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private AuditEvent event(AuditAction action, String result, String detail) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("result", result);
        if (detail != null) {
            meta.put("detail", detail);
        }
        return new AuditEvent(action, userId, vaultId, "X", entityId, null, meta);
    }

    private PaymentReceiptData receipt() {
        return new PaymentReceiptData("R-1", entityId, "An", "an@example.com", "VIP", "VIP Thang", 30, 99000L,
                "T", "NCB", null, null, null, 1L, "VNPay");
    }

    @Test
    void paymentSucceeded_sendsReceiptAndActivation() {
        byte[] pdf = {1};
        LocalDateTime until = LocalDateTime.of(2026, 12, 9, 0, 0);
        when(receiptService.getReceiptData(entityId, userId)).thenReturn(receipt());
        when(pdfService.render(any())).thenReturn(pdf);
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(Subscription.builder().expiresAt(until).build()));

        listener.handle(event(AuditAction.PAYMENT_SUCCEEDED, "SUCCESS", "VIP"));

        verify(emailSender).sendPaymentSucceededNotice("an@example.com", "An", "VIP Thang", 99000L, "R-1", pdf);
        verify(emailSender).sendVipActivatedNotice("an@example.com", "An", "VIP Thang", until);
    }

    @Test
    void paymentSucceeded_pdfFails_stillSendsMailWithoutAttachment() {
        when(receiptService.getReceiptData(entityId, userId)).thenReturn(receipt());
        when(pdfService.render(any())).thenThrow(new IllegalStateException("font"));
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        listener.handle(event(AuditAction.PAYMENT_SUCCEEDED, "SUCCESS", "VIP"));

        verify(emailSender).sendPaymentSucceededNotice(eq("an@example.com"), eq("An"), eq("VIP Thang"), eq(99000L),
                eq("R-1"), isNull());
        verify(emailSender).sendVipActivatedNotice("an@example.com", "An", "VIP Thang", null);
    }

    @Test
    void paymentFailed_sendsNotice() {
        listener.handle(event(AuditAction.PAYMENT_FAILED, "FAILURE", "vnp_ResponseCode=24"));

        verify(emailSender).sendPaymentFailedNotice("an@example.com", "An");
    }

}
