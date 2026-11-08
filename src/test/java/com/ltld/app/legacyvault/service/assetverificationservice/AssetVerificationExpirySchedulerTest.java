package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.AssetVerification;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.AssetVerificationStatus;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.event.AssetReverificationNeededEvent;
import com.ltld.app.legacyvault.repository.AssetVerificationRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetVerificationExpirySchedulerTest {

    @Mock private AssetVerificationRepository verificationRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PlatformTransactionManager transactionManager;

    private AssetVerificationExpiryScheduler scheduler;
    private final LocalDateTime now = LocalDateTime.of(2027, 3, 1, 9, 0);
    private final User owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
    private Vault vault;
    private DigitalAsset asset;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        scheduler = new AssetVerificationExpiryScheduler(verificationRepository, notificationRepository,
                auditLogService, eventPublisher, transactionManager, 14);
        vault = Vault.builder().id(UUID.randomUUID()).owner(owner).name("V").status(VaultStatus.ACTIVE).build();
        asset = DigitalAsset.builder().id(UUID.randomUUID()).vault(vault).assetType(AssetType.EMAIL)
                .assetName("Gmail chính").publicFieldsJson("{\"platform\":\"Gmail\"}")
                .status(AssetStatus.ACTIVE).build();
    }

    private void stubDue(AssetVerification v) {
        when(verificationRepository.findDueForReverification(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(v));
        when(verificationRepository.findById(v.getId())).thenReturn(Optional.of(v));
    }

    private AssetVerification verifiedRow(LocalDateTime expiresAt, String fingerprint) {
        return AssetVerification.builder().id(UUID.randomUUID()).asset(asset)
                .method(AssetVerificationMethod.OTP_EMAIL).provider(VerificationProvider.REAL)
                .status(AssetVerificationStatus.VERIFIED).fingerprint(fingerprint)
                .verifiedAt(expiresAt.minusDays(180)).expiresAt(expiresAt).build();
    }

    @Test
    void expiredRow_isMarkedExpired_ownerNotified_eventPublished() {
        AssetVerification v = verifiedRow(now.minusDays(1), AssetFingerprint.compute(asset));
        stubDue(v);

        scheduler.processDue(now);

        assertThat(v.getStatus()).isEqualTo(AssetVerificationStatus.EXPIRED);
        ArgumentCaptor<Notification> n = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(n.capture());
        assertThat(n.getValue().getType()).isEqualTo(NotificationType.ASSET_REVERIFY_NEEDED);
        assertThat(n.getValue().getRecipient()).isSameAs(owner);
        assertThat(n.getValue().getReferenceId()).isEqualTo(asset.getId());
        verify(eventPublisher).publishEvent(any(AssetReverificationNeededEvent.class));
        verify(auditLogService).log(eq(AuditAction.ASSET_VERIFICATION_EXPIRED), eq(AuditResult.SUCCESS), isNull(),
                isNull(), eq(vault.getId()), eq("DigitalAsset"), eq(asset.getId().toString()), any());
    }

    @Test
    void expiringSoon_isRemindedOnce_statusUnchanged() {
        AssetVerification v = verifiedRow(now.plusDays(3), AssetFingerprint.compute(asset));
        stubDue(v);

        scheduler.processDue(now);

        assertThat(v.getStatus()).isEqualTo(AssetVerificationStatus.VERIFIED);
        assertThat(v.getRemindedAt()).isEqualTo(now);
        verify(notificationRepository).save(any(Notification.class));
        verify(auditLogService).log(eq(AuditAction.ASSET_VERIFICATION_REMINDED), eq(AuditResult.SUCCESS), isNull(),
                isNull(), eq(vault.getId()), eq("DigitalAsset"), eq(asset.getId().toString()), any());
    }

    @Test
    void alreadyReminded_andNotYetExpired_isSkipped() {
        AssetVerification v = verifiedRow(now.plusDays(3), AssetFingerprint.compute(asset));
        v.setRemindedAt(now.minusDays(1));
        stubDue(v);

        scheduler.processDue(now);

        verify(notificationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(AssetReverificationNeededEvent.class));
    }

    @Test
    void assetEditedAfterVerification_becomesSelfDeclared_withoutNotifying() {
        AssetVerification v = verifiedRow(now.minusDays(1), "fingerprint-cu");
        stubDue(v);

        scheduler.processDue(now);

        assertThat(v.getStatus()).isEqualTo(AssetVerificationStatus.SELF_DECLARED);
        verify(notificationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(AssetReverificationNeededEvent.class));
    }
}
