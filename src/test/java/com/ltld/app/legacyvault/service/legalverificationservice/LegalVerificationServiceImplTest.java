package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.dto.legalverificationdto.DocumentVerificationResponseDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.RejectVerificationRequestDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.SignVerificationRequestDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationRequestResponseDto;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VerificationRequestVault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.SignatureMethod;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.ConflictOfInterestException;
import com.ltld.app.legacyvault.exception.InvalidDateRangeException;
import com.ltld.app.legacyvault.exception.InvalidSignCodeException;
import com.ltld.app.legacyvault.exception.NotAssignedVerifierException;
import com.ltld.app.legacyvault.exception.RequestAlreadyReviewedException;
import com.ltld.app.legacyvault.exception.RequestNotApprovedException;
import com.ltld.app.legacyvault.exception.TooManyOtpRequestsException;
import com.ltld.app.legacyvault.exception.VaultStateConflictException;
import com.ltld.app.legacyvault.exception.VerificationRequestNotFoundException;
import com.ltld.app.legacyvault.repository.DigitalSignatureRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import com.ltld.app.legacyvault.utility.OtpHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OtpGenerator otpGenerator;

    @Mock
    private OtpHasher otpHasher;

    @Mock
    private EmailSender emailSender;

    @InjectMocks
    private LegalVerificationServiceImpl service;

    private UUID requestId;
    private UUID verifierId;
    private UUID ownerId;
    private LegalVerificationRequest pendingRequest;

    @BeforeEach
    void setUp() {
        requestId = UUID.randomUUID();
        verifierId = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        pendingRequest = LegalVerificationRequest.builder()
                .id(requestId)
                .owner(User.builder().id(ownerId).fullName("Test Owner").build())
                .executor(User.builder().id(UUID.randomUUID()).fullName("Test Executor").build())
                .deathCertificateFileUrlEncrypted("enc://test.pdf")
                .status(LegalVerificationStatus.PENDING)
                .build();
    }

    // ---------- reject ----------

    @Test
    void reject_success_setsStatusRejectedAndSavesReason() {
        RejectVerificationRequestDto dto = new RejectVerificationRequestDto();
        dto.setReason("Giấy chứng tử không hợp lệ");

        User verifierUser = User.builder().id(verifierId).build();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.getReferenceById(verifierId)).thenReturn(verifierUser);
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

    @Test
    void reject_verifierIsExecutor_throwsConflictOfInterest() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
        RejectVerificationRequestDto dto = new RejectVerificationRequestDto();
        dto.setReason("test");

        assertThatThrownBy(() -> service.reject(requestId, pendingRequest.getExecutor().getId(), dto))
                .isInstanceOf(ConflictOfInterestException.class);

        verify(requestRepository, never()).save(any());
    }

    // ---------- approve (UC12) ----------

    @Test
    void approve_success_setsApprovedWithoutSigningOrUnlocking() {
        User verifierUser = User.builder().id(verifierId).build();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.getReferenceById(verifierId)).thenReturn(verifierUser);
        when(requestRepository.save(any(LegalVerificationRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.approve(requestId, verifierId);

        assertThat(pendingRequest.getStatus()).isEqualTo(LegalVerificationStatus.APPROVED);
        assertThat(pendingRequest.getDecidedAt()).isNotNull();
        verifyNoInteractions(signatureRepository);
    }

    @Test
    void approve_alreadyRejected_throwsAlreadyReviewedException() {
        pendingRequest.setStatus(LegalVerificationStatus.REJECTED);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.approve(requestId, verifierId))
                .isInstanceOf(RequestAlreadyReviewedException.class);

        verify(requestRepository, never()).save(any());
    }

    @Test
    void approve_verifierIsOwner_throwsConflictOfInterest() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.approve(requestId, ownerId))
                .isInstanceOf(ConflictOfInterestException.class);

        verify(requestRepository, never()).save(any());
    }

    // ---------- sign (UC14) ----------

    private SignVerificationRequestDto signDto() {
        SignVerificationRequestDto dto = new SignVerificationRequestDto();
        dto.setMethod(SignatureMethod.MOCK_OTP);
        dto.setCode("000000");
        return dto;
    }

    private void approvedByVerifier() {
        pendingRequest.setStatus(LegalVerificationStatus.APPROVED);
        pendingRequest.setVerifier(User.builder().id(verifierId).build());
    }

    private Vault vaultOf(VaultStatus status) {
        return Vault.builder().id(UUID.randomUUID()).status(status).owner(pendingRequest.getOwner()).build();
    }

    @Test
    void sign_success_savesSignatureUnlocksVaultAndSetsClaimDeadline() {
        approvedByVerifier();
        pendingRequest.setSignOtpHash("hashedOtp");
        pendingRequest.setSignOtpExpiresAt(LocalDateTime.now().plusMinutes(5));

        Vault vault = vaultOf(VaultStatus.ACTIVE);
        VerificationRequestVault link = VerificationRequestVault.builder()
                .request(pendingRequest).vault(vault).build();

        User verifierUser = User.builder().id(verifierId).build();
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.getReferenceById(verifierId)).thenReturn(verifierUser);
        when(otpHasher.matches(requestId, "000000", "hashedOtp")).thenReturn(true);
        when(requestVaultRepository.findByRequestIdFetchVault(requestId)).thenReturn(List.of(link));

        service.sign(requestId, verifierId, signDto());

        assertThat(vault.getStatus()).isEqualTo(VaultStatus.UNLOCKED);
        assertThat(vault.getUnlockedAt()).isNotNull();
        assertThat(vault.getClaimDeadlineAt()).isEqualTo(vault.getUnlockedAt().plusDays(60));
        verify(signatureRepository, times(1)).save(any());
        verify(auditLogService, times(1)).log(
                eq(AuditAction.VAULT_UNLOCK), eq(AuditResult.SUCCESS), eq(verifierId),
                isNull(), eq("Vault"), eq(vault.getId().toString()), anyString());
    }

    @Test
    void sign_wrongCode_throwsInvalidSignCode_andDoesNotUnlock() {
        approvedByVerifier();
        pendingRequest.setSignOtpHash("hashedOtp");
        pendingRequest.setSignOtpExpiresAt(LocalDateTime.now().plusMinutes(5));

        SignVerificationRequestDto badDto = new SignVerificationRequestDto();
        badDto.setMethod(SignatureMethod.MOCK_OTP);
        badDto.setCode("111111");

        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));
        when(otpHasher.matches(requestId, "111111", "hashedOtp")).thenReturn(false);

        assertThatThrownBy(() -> service.sign(requestId, verifierId, badDto))
                .isInstanceOf(InvalidSignCodeException.class);

        verify(signatureRepository, never()).save(any());
        verifyNoInteractions(requestVaultRepository);
    }

    @Test
    void sign_expiredOtp_throwsInvalidSignCode() {
        approvedByVerifier();
        pendingRequest.setSignOtpHash("hashedOtp");
        pendingRequest.setSignOtpExpiresAt(LocalDateTime.now().minusMinutes(1)); // hết hạn

        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.sign(requestId, verifierId, signDto()))
                .isInstanceOf(InvalidSignCodeException.class);

        verify(signatureRepository, never()).save(any());
    }

    // T5: method = MOCK_PIN bị từ chối (N4)
    @Test
    void sign_methodMockPin_throwsInvalidSignCode() {
        SignVerificationRequestDto dto = new SignVerificationRequestDto();
        dto.setMethod(SignatureMethod.MOCK_PIN);
        dto.setCode("123456");

        assertThatThrownBy(() -> service.sign(requestId, verifierId, dto))
                .isInstanceOf(InvalidSignCodeException.class);

        verifyNoInteractions(signatureRepository, eventPublisher);
    }

    @Test
    void sendSignOtp_success_generatesAndSendsOtp() {
        approvedByVerifier();
        User verifierUser = User.builder().id(verifierId).email("verifier@test.com").build();

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.findById(verifierId)).thenReturn(Optional.of(verifierUser));
        when(otpGenerator.generate()).thenReturn("123456");
        when(otpHasher.hash(requestId, "123456")).thenReturn("hashed123456");

        service.sendSignOtp(requestId, verifierId);

        assertThat(pendingRequest.getSignOtpHash()).isEqualTo("hashed123456");
        assertThat(pendingRequest.getSignOtpExpiresAt()).isNotNull();
        assertThat(pendingRequest.getSignAttempts()).isEqualTo(0);
        verify(emailSender, times(1)).sendEmail("verifier@test.com", "123456");
    }

    // T4: gửi lại OTP trong 60 giây bị chặn (N1)
    @Test
    void sendSignOtp_resendWithin60Seconds_throwsTooManyRequests() {
        approvedByVerifier();
        pendingRequest.setSignOtpSentAt(LocalDateTime.now().minusSeconds(10));
        User verifierUser = User.builder().id(verifierId).email("verifier@test.com").build();

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.findById(verifierId)).thenReturn(Optional.of(verifierUser));
        when(otpGenerator.generate()).thenReturn("123456");

        assertThatThrownBy(() -> service.sendSignOtp(requestId, verifierId))
                .isInstanceOf(TooManyOtpRequestsException.class);

        verifyNoInteractions(emailSender);
    }

    @Test
    void sign_requestNotApproved_throwsAndDoesNotSignOrUnlock() {
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.sign(requestId, verifierId, signDto()))
                .isInstanceOf(RequestNotApprovedException.class);

        verify(signatureRepository, never()).save(any());
        verifyNoInteractions(requestVaultRepository, auditLogService);
    }

    @Test
    void sign_differentVerifier_throwsNotAssignedAndDoesNotSign() {
        approvedByVerifier();
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.sign(requestId, UUID.randomUUID(), signDto()))
                .isInstanceOf(NotAssignedVerifierException.class);

        verify(signatureRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void sign_alreadySigned_throwsAndDoesNotSignAgain() {
        approvedByVerifier();
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));
        when(signatureRepository.existsByRequestId(requestId)).thenReturn(true);

        assertThatThrownBy(() -> service.sign(requestId, verifierId, signDto()))
                .isInstanceOf(RequestAlreadyReviewedException.class);

        verify(signatureRepository, never()).save(any());
    }

    @Test
    void sign_vaultNotActive_throwsAndDoesNotSignOrUnlock() {
        approvedByVerifier();
        pendingRequest.setSignOtpHash("hashedOtp");
        pendingRequest.setSignOtpExpiresAt(LocalDateTime.now().plusMinutes(5));

        Vault archived = vaultOf(VaultStatus.ARCHIVED_LOCKED);
        VerificationRequestVault link = VerificationRequestVault.builder()
                .request(pendingRequest).vault(archived).build();
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));
        when(otpHasher.matches(requestId, "000000", "hashedOtp")).thenReturn(true);
        when(requestVaultRepository.findByRequestIdFetchVault(requestId)).thenReturn(List.of(link));

        assertThatThrownBy(() -> service.sign(requestId, verifierId, signDto()))
                .isInstanceOf(VaultStateConflictException.class);

        assertThat(archived.getStatus()).isEqualTo(VaultStatus.ARCHIVED_LOCKED);
        verify(signatureRepository, never()).save(any());
    }

    @Test
    void sign_vaultOfAnotherOwner_throwsConflict() {
        approvedByVerifier();
        pendingRequest.setSignOtpHash("hashedOtp");
        pendingRequest.setSignOtpExpiresAt(LocalDateTime.now().plusMinutes(5));

        Vault foreign = Vault.builder().id(UUID.randomUUID()).status(VaultStatus.ACTIVE)
                .owner(User.builder().id(UUID.randomUUID()).build()).build();
        VerificationRequestVault link = VerificationRequestVault.builder()
                .request(pendingRequest).vault(foreign).build();
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));
        when(otpHasher.matches(requestId, "000000", "hashedOtp")).thenReturn(true);
        when(requestVaultRepository.findByRequestIdFetchVault(requestId)).thenReturn(List.of(link));

        assertThatThrownBy(() -> service.sign(requestId, verifierId, signDto()))
                .isInstanceOf(VaultStateConflictException.class);

        verify(signatureRepository, never()).save(any());
    }

    @Test
    void sign_noVaultLinked_throwsConflict() {
        approvedByVerifier();
        pendingRequest.setSignOtpHash("hashedOtp");
        pendingRequest.setSignOtpExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));
        when(otpHasher.matches(requestId, "000000", "hashedOtp")).thenReturn(true);
        when(requestVaultRepository.findByRequestIdFetchVault(requestId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.sign(requestId, verifierId, signDto()))
                .isInstanceOf(VaultStateConflictException.class);

        verify(signatureRepository, never()).save(any());
    }

    // ---------- UC13: verifyDocument (với kiểm tra N6) ----------

    @Test
    void verifyDocument_supportedFormat_returnsValid() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

        DocumentVerificationResponseDto result = service.verifyDocument(requestId, verifierId);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void verifyDocument_unsupportedFormat_returnsInvalid() {
        pendingRequest.setDeathCertificateFileUrlEncrypted("enc://test.exe");
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThat(service.verifyDocument(requestId, verifierId).isValid()).isFalse();
    }

    @Test
    void verifyDocument_requestNotFound_throwsNotFoundException() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyDocument(requestId, verifierId))
                .isInstanceOf(VerificationRequestNotFoundException.class);
    }

    // N6: Xung đột lợi ích khi xem chi tiết hoặc verifyDocument -> 404
    @Test
    void getById_verifierIsOwner_throwsNotFoundException() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.getById(requestId, ownerId))
                .isInstanceOf(VerificationRequestNotFoundException.class);
    }

    @Test
    void verifyDocument_verifierIsExecutor_throwsNotFoundException() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.verifyDocument(requestId, pendingRequest.getExecutor().getId()))
                .isInstanceOf(VerificationRequestNotFoundException.class);
    }

    // R1: toDto phải trả đúng createdAt
    @Test
    void getById_returnsCreatedAt() {
        pendingRequest.setCreatedAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThat(service.getById(requestId, verifierId).getCreatedAt())
                .isEqualTo(LocalDateTime.of(2026, 10, 1, 9, 0));
    }

    // T7: danh sách PENDING phải dùng query đã loại Owner/Executor (N6)
    @Test
    void getPendingRequests_usesVisibleToQuery() {
        when(requestRepository.findPendingVisibleTo(LegalVerificationStatus.PENDING, verifierId))
                .thenReturn(List.of(pendingRequest));
        when(requestVaultRepository.findByRequestId(requestId)).thenReturn(List.of());

        List<VerificationRequestResponseDto> result = service.getPendingRequests(verifierId);

        assertThat(result).hasSize(1);
        verify(requestRepository).findPendingVisibleTo(LegalVerificationStatus.PENDING, verifierId);
        verify(requestRepository, never()).findByStatusFetch(any());
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

    @Test
    void getHistory_fromAfterTo_throwsInvalidDateRangeException() {
        LocalDate from = LocalDate.of(2026, 10, 10);
        LocalDate to = LocalDate.of(2026, 10, 5);

        assertThatThrownBy(() -> service.getHistory(verifierId, null, from, to))
                .isInstanceOf(InvalidDateRangeException.class);
    }
}