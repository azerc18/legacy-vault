package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.repository.DigitalSignatureRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovedRequestExpirySchedulerTest {

    @Mock private LegalVerificationRequestRepository requestRepository;
    @Mock private DigitalSignatureRepository signatureRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private PlatformTransactionManager transactionManager;

    private ApprovedRequestExpiryScheduler scheduler;

    private final LocalDateTime now = LocalDateTime.of(2026, 11, 10, 12, 0);
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        scheduler = new ApprovedRequestExpiryScheduler(requestRepository, signatureRepository,
                auditLogService, transactionManager);
    }

    private LegalVerificationRequest approved(LocalDateTime decidedAt) {
        return LegalVerificationRequest.builder().id(id)
                .status(LegalVerificationStatus.APPROVED)
                .verifier(User.builder().id(UUID.randomUUID()).build())
                .decidedAt(decidedAt)
                .signOtpHash("hash").signAttempts(3)
                .build();
    }

    private void foundExpired() {
        when(requestRepository.findExpiredUnsignedIds(eq(LegalVerificationStatus.APPROVED),
                eq(now.minusDays(ApprovedRequestExpiryScheduler.APPROVED_EXPIRY_DAYS)), any(Pageable.class)))
                .thenReturn(List.of(id));
    }

    @Test
    void reopenExpired_expiredUnsigned_backToPendingAndAudited() {
        LegalVerificationRequest r = approved(now.minusDays(8));
        foundExpired();
        when(requestRepository.findByIdForUpdate(id)).thenReturn(Optional.of(r));
        when(signatureRepository.existsByRequestId(id)).thenReturn(false);

        scheduler.reopenExpired(now);

        assertThat(r.getStatus()).isEqualTo(LegalVerificationStatus.PENDING);
        assertThat(r.getVerifier()).isNull();
        assertThat(r.getDecidedAt()).isNull();
        assertThat(r.getSignOtpHash()).isNull();
        assertThat(r.getSignAttempts()).isZero();
        verify(requestRepository).save(r);
        verify(auditLogService).log(eq(AuditAction.LEGAL_REQUEST_REOPENED), eq(AuditResult.SUCCESS),
                isNull(), isNull(), eq("LegalVerificationRequest"), eq(id.toString()), anyString());
    }

    @Test
    void reopenExpired_nothingExpired_doesNothing() {
        when(requestRepository.findExpiredUnsignedIds(any(), any(), any(Pageable.class))).thenReturn(List.of());

        scheduler.reopenExpired(now);

        verify(requestRepository, never()).save(any());
    }

    @Test
    void reopenExpired_signedMeanwhile_skips() {
        LegalVerificationRequest r = approved(now.minusDays(8));
        foundExpired();
        when(requestRepository.findByIdForUpdate(id)).thenReturn(Optional.of(r));
        when(signatureRepository.existsByRequestId(id)).thenReturn(true);

        scheduler.reopenExpired(now);

        assertThat(r.getStatus()).isEqualTo(LegalVerificationStatus.APPROVED);
        verify(requestRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), anyString(), anyString(), any());
    }

    @Test
    void reopenExpired_rejectedMeanwhile_skips() {
        LegalVerificationRequest r = approved(now.minusDays(8));
        r.setStatus(LegalVerificationStatus.REJECTED);
        foundExpired();
        when(requestRepository.findByIdForUpdate(id)).thenReturn(Optional.of(r));

        scheduler.reopenExpired(now);

        assertThat(r.getStatus()).isEqualTo(LegalVerificationStatus.REJECTED);
        verify(requestRepository, never()).save(any());
    }
}
