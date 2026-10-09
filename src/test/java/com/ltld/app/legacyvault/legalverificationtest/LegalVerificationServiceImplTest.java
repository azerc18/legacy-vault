package com.ltld.app.legacyvault.legalverificationtest;

import com.ltld.app.legacyvault.dto.DocumentVerificationResponseDto;
import com.ltld.app.legacyvault.dto.RejectVerificationRequestDto;
import com.ltld.app.legacyvault.dto.SignVerificationRequestDto;
import com.ltld.app.legacyvault.dto.VerificationRequestResponseDto;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VerificationRequestVault;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.SignatureMethod;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.RequestAlreadyReviewedException;
import com.ltld.app.legacyvault.exception.RequestNotApprovedException;
import com.ltld.app.legacyvault.exception.VerificationRequestNotFoundException;
import com.ltld.app.legacyvault.repository.DigitalSignatureRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
import com.ltld.app.legacyvault.service.impl.LegalVerificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LegalVerificationServiceImplTest {

    @Mock
    private LegalVerificationRequestRepository requestRepository;

    @Mock
    private DigitalSignatureRepository signatureRepository;

    @Mock
    private VerificationRequestVaultRepository requestVaultRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private LegalVerificationServiceImpl service;

    private UUID requestId;
    private UUID verifierId;
    private LegalVerificationRequest pendingRequest;

    @BeforeEach
    void setUp() {
        requestId = UUID.randomUUID();
        verifierId = UUID.randomUUID();

        pendingRequest = LegalVerificationRequest.builder()
                .id(requestId)
                .owner(User.builder().fullName("Test Owner").build())
                .executor(User.builder().fullName("Test Executor").build())
                .deathCertificateFileUrlEncrypted("enc://test.pdf")
                .status(LegalVerificationStatus.PENDING)
                .build();
    }

    // ---------- reject ----------

    @Test
    void reject_success_setsStatusRejectedAndSavesReason() {
        RejectVerificationRequestDto dto = new RejectVerificationRequestDto();
        dto.setReason("Giấy chứng tử không hợp lệ");

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
        when(requestRepository.save(any(LegalVerificationRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.reject(requestId, verifierId, dto);

        assertThat(pendingRequest.getStatus()).isEqualTo(LegalVerificationStatus.REJECTED);
        assertThat(pendingRequest.getRejectionReason()).isEqualTo("Giấy chứng tử không hợp lệ");
        assertThat(pendingRequest.getDecidedAt()).isNotNull();
    }

    @Test
    void reject_requestNotFound_throwsNotFoundException() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reject(requestId, verifierId, new RejectVerificationRequestDto()))
                .isInstanceOf(VerificationRequestNotFoundException.class);

        verify(requestRepository, never()).save(any());
    }

    @Test
    void reject_alreadyApproved_throwsAlreadyReviewedException() {
        pendingRequest.setStatus(LegalVerificationStatus.APPROVED);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.reject(requestId, verifierId, new RejectVerificationRequestDto()))
                .isInstanceOf(RequestAlreadyReviewedException.class);

        verify(requestRepository, never()).save(any());
    }

    // ---------- approve (UC12) ----------

    @Test
    void approve_success_setsApprovedWithoutSigningOrUnlocking() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
        when(requestRepository.save(any(LegalVerificationRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.approve(requestId, verifierId);

        assertThat(pendingRequest.getStatus()).isEqualTo(LegalVerificationStatus.APPROVED);
        assertThat(pendingRequest.getDecidedAt()).isNotNull();
        verifyNoInteractions(signatureRepository, requestVaultRepository);
    }

    @Test
    void approve_alreadyRejected_throwsAlreadyReviewedException() {
        pendingRequest.setStatus(LegalVerificationStatus.REJECTED);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.approve(requestId, verifierId))
                .isInstanceOf(RequestAlreadyReviewedException.class);

        verify(requestRepository, never()).save(any());
    }

    // ---------- sign (UC14) ----------

    @Test
    void sign_success_savesSignatureAndUnlocksVault() {
        pendingRequest.setStatus(LegalVerificationStatus.APPROVED);
        Vault vault = Vault.builder().status(VaultStatus.ACTIVE).build();
        VerificationRequestVault link = VerificationRequestVault.builder()
                .request(pendingRequest)
                .vault(vault)
                .build();

        SignVerificationRequestDto dto = new SignVerificationRequestDto();
        dto.setMethod(SignatureMethod.MOCK_OTP);
        dto.setCode("123456");

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
        when(requestVaultRepository.findByRequestId(requestId)).thenReturn(List.of(link));

        service.sign(requestId, verifierId, dto);

        assertThat(vault.getStatus()).isEqualTo(VaultStatus.UNLOCKED);
        assertThat(vault.getUnlockedAt()).isNotNull();
        verify(signatureRepository, times(1)).save(any());
        verify(auditLogService, times(1)).log(
                eq(AuditAction.VAULT_UNLOCK), eq(AuditResult.SUCCESS), eq(verifierId),
                isNull(), eq("Vault"), isNull(), anyString());
    }

    @Test
    void sign_requestNotApproved_throwsAndDoesNotSignOrUnlock() {
        // pendingRequest đang PENDING, chưa được duyệt
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

        SignVerificationRequestDto dto = new SignVerificationRequestDto();
        dto.setMethod(SignatureMethod.MOCK_OTP);
        dto.setCode("123456");

        assertThatThrownBy(() -> service.sign(requestId, verifierId, dto))
                .isInstanceOf(RequestNotApprovedException.class);

        verify(signatureRepository, never()).save(any());
        verify(requestVaultRepository, never()).findByRequestId(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void sign_alreadySigned_throwsAndDoesNotSignAgain() {
        pendingRequest.setStatus(LegalVerificationStatus.APPROVED);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
        when(signatureRepository.existsByRequestId(requestId)).thenReturn(true);

        SignVerificationRequestDto dto = new SignVerificationRequestDto();
        dto.setMethod(SignatureMethod.MOCK_OTP);
        dto.setCode("123456");

        assertThatThrownBy(() -> service.sign(requestId, verifierId, dto))
                .isInstanceOf(RequestAlreadyReviewedException.class);

        verify(signatureRepository, never()).save(any());
    }

    // ---------- UC13: verifyDocument ----------

    @Test
    void verifyDocument_supportedFormat_returnsValid() {
        // pendingRequest dùng "enc://test.pdf"
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

        DocumentVerificationResponseDto result = service.verifyDocument(requestId);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void verifyDocument_unsupportedFormat_returnsInvalid() {
        pendingRequest.setDeathCertificateFileUrlEncrypted("enc://test.exe");
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThat(service.verifyDocument(requestId).isValid()).isFalse();
    }

    @Test
    void verifyDocument_requestNotFound_throwsNotFoundException() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyDocument(requestId))
                .isInstanceOf(VerificationRequestNotFoundException.class);
    }

    // ---------- UC15: getHistory ----------


    @Test
    void getHistory_noFilter_returnsAllRequestsHandledByVerifier() {
        pendingRequest.setStatus(LegalVerificationStatus.APPROVED);
        when(requestRepository.findByVerifierIdOrderByDecidedAtDesc(verifierId))
                .thenReturn(List.of(pendingRequest));

        List<VerificationRequestResponseDto> result = service.getHistory(verifierId, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(LegalVerificationStatus.APPROVED);
    }

    @Test
    void getHistory_noHandledRequests_returnsEmptyList() {
        when(requestRepository.findByVerifierIdOrderByDecidedAtDesc(verifierId)).thenReturn(List.of());

        assertThat(service.getHistory(verifierId, null, null, null)).isEmpty();
    }

    @Test
    void getHistory_filterByStatus_returnsOnlyMatchingStatus() {
        pendingRequest.setStatus(LegalVerificationStatus.APPROVED);
        LegalVerificationRequest rejected = LegalVerificationRequest.builder()
                .id(UUID.randomUUID())
                .owner(User.builder().fullName("Test Owner").build())
                .executor(User.builder().fullName("Test Executor").build())
                .deathCertificateFileUrlEncrypted("enc://other.pdf")
                .status(LegalVerificationStatus.REJECTED)
                .build();
        when(requestRepository.findByVerifierIdOrderByDecidedAtDesc(verifierId))
                .thenReturn(List.of(pendingRequest, rejected));

        List<VerificationRequestResponseDto> result =
                service.getHistory(verifierId, LegalVerificationStatus.REJECTED, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(LegalVerificationStatus.REJECTED);
    }

    @Test
    void getHistory_filterByDateRange_returnsOnlyRequestsInsideRange() {
        pendingRequest.setStatus(LegalVerificationStatus.APPROVED);
        pendingRequest.setDecidedAt(LocalDateTime.of(2026, 10, 5, 10, 0));
        LegalVerificationRequest older = LegalVerificationRequest.builder()
                .id(UUID.randomUUID())
                .owner(User.builder().fullName("Test Owner").build())
                .executor(User.builder().fullName("Test Executor").build())
                .deathCertificateFileUrlEncrypted("enc://old.pdf")
                .status(LegalVerificationStatus.APPROVED)
                .decidedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
        when(requestRepository.findByVerifierIdOrderByDecidedAtDesc(verifierId))
                .thenReturn(List.of(pendingRequest, older));

        List<VerificationRequestResponseDto> result = service.getHistory(
                verifierId, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDecidedAt()).isEqualTo(LocalDateTime.of(2026, 10, 5, 10, 0));
    }
}