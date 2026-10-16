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
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationDocumentDto;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
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

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private CryptoService cryptoService;

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
        User verifierUser = User.builder().id(verifierId).email("verifier@test.com").build();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.getReferenceById(verifierId)).thenReturn(verifierUser);
        when(requestRepository.save(any(LegalVerificationRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.approve(requestId, verifierId);

        assertThat(pendingRequest.getStatus()).isEqualTo(LegalVerificationStatus.APPROVED);
        assertThat(pendingRequest.getDecidedAt()).isNotNull();
        verifyNoInteractions(signatureRepository);
        // M3: audit phải có email người duyệt
        verify(auditLogService).log(eq(AuditAction.LEGAL_REQUEST_APPROVED), eq(AuditResult.SUCCESS),
                eq(verifierId), eq("verifier@test.com"),
                eq("LegalVerificationRequest"), eq(requestId.toString()), isNull());
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

    // T2: sai quá 5 lần -> hủy mã + audit "Too many sign attempts"
    @Test
    void sign_tooManyAttempts_invalidatesOtpAndAudits() {
        approvedByVerifier();
        pendingRequest.setSignOtpHash("hashedOtp");
        pendingRequest.setSignOtpExpiresAt(LocalDateTime.now().plusMinutes(5));
        pendingRequest.setSignAttempts(5); // lần thứ 6 vượt giới hạn

        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.sign(requestId, verifierId, signDto()))
                .isInstanceOf(InvalidSignCodeException.class);

        assertThat(pendingRequest.getSignOtpHash()).isNull();
        verify(auditLogService).log(eq(AuditAction.LEGAL_SIGN_FAILED), eq(AuditResult.FAILURE),
                eq(verifierId), any(), eq("LegalVerificationRequest"), eq(requestId.toString()),
                eq("Too many sign attempts"));
        verify(signatureRepository, never()).save(any());
    }

    // T4: sendSignOtp chưa APPROVED / sai verifier / đã ký -> bị chặn, không gửi email
    @Test
    void sendSignOtp_notApproved_throwsAndDoesNotSendEmail() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.sendSignOtp(requestId, verifierId))
                .isInstanceOf(RequestNotApprovedException.class);

        verifyNoInteractions(emailSender);
    }

    @Test
    void sendSignOtp_differentVerifier_throwsNotAssignedAndDoesNotSendEmail() {
        approvedByVerifier();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.sendSignOtp(requestId, UUID.randomUUID()))
                .isInstanceOf(NotAssignedVerifierException.class);

        verifyNoInteractions(emailSender);
    }

    @Test
    void sendSignOtp_alreadySigned_throwsAndDoesNotSendEmail() {
        approvedByVerifier();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
        when(signatureRepository.existsByRequestId(requestId)).thenReturn(true);

        assertThatThrownBy(() -> service.sendSignOtp(requestId, verifierId))
                .isInstanceOf(RequestAlreadyReviewedException.class);

        verifyNoInteractions(emailSender);
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
        // N3: có audit việc gửi mã ký
        verify(auditLogService).log(eq(AuditAction.LEGAL_SIGN_OTP_SENT), eq(AuditResult.SUCCESS),
                eq(verifierId), eq("verifier@test.com"),
                eq("LegalVerificationRequest"), eq(requestId.toString()), isNull());
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

    // T7: danh sách PENDING dùng query đã loại Owner/Executor (N6) và lấy vault theo lô (M1)
    @Test
    void getPendingRequests_usesVisibleToQuery_andLoadsVaultsInOneBatch() {
        when(requestRepository.findPendingVisibleTo(LegalVerificationStatus.PENDING, verifierId))
                .thenReturn(List.of(pendingRequest));

        List<VerificationRequestResponseDto> result = service.getPendingRequests(verifierId);

        assertThat(result).hasSize(1);
        verify(requestRepository).findPendingVisibleTo(LegalVerificationStatus.PENDING, verifierId);
        verify(requestVaultRepository, times(1)).findByRequestIdIn(anyCollection());
        verify(requestVaultRepository, never()).findByRequestId(any());
    }

    // M1: mỗi hồ sơ nhận đúng vault của mình dù chỉ 1 query
    @Test
    void getPendingRequests_mapsVaultIdsPerRequest() {
        LegalVerificationRequest second = LegalVerificationRequest.builder()
                .id(UUID.randomUUID())
                .owner(User.builder().id(UUID.randomUUID()).fullName("Owner 2").build())
                .executor(User.builder().id(UUID.randomUUID()).fullName("Executor 2").build())
                .deathCertificateFileUrlEncrypted("enc://second.pdf")
                .status(LegalVerificationStatus.PENDING)
                .build();
        UUID vault1 = UUID.randomUUID();
        UUID vault2 = UUID.randomUUID();
        VerificationRequestVault link1 = VerificationRequestVault.builder()
                .request(pendingRequest).vault(Vault.builder().id(vault1).build()).build();
        VerificationRequestVault link2 = VerificationRequestVault.builder()
                .request(second).vault(Vault.builder().id(vault2).build()).build();

        when(requestRepository.findPendingVisibleTo(LegalVerificationStatus.PENDING, verifierId))
                .thenReturn(List.of(pendingRequest, second));
        when(requestVaultRepository.findByRequestIdIn(anyCollection())).thenReturn(List.of(link1, link2));

        List<VerificationRequestResponseDto> result = service.getPendingRequests(verifierId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getVaultIds()).containsExactly(vault1);
        assertThat(result.get(1).getVaultIds()).containsExactly(vault2);
        verify(requestVaultRepository, never()).findByRequestId(any());
    }

    // ---------- UC15: getHistory (lọc + phân trang trong DB) ----------

    @Test
    void getHistory_noFilter_returnsAllRequestsHandledByVerifier() {
        pendingRequest.setStatus(LegalVerificationStatus.APPROVED);
        when(requestRepository.findHistory(eq(verifierId), anyCollection(),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(pendingRequest));

        List<VerificationRequestResponseDto> result = service.getHistory(verifierId, null, null, null, 0, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(LegalVerificationStatus.APPROVED);
    }

    @Test
    void getHistory_noHandledRequests_returnsEmptyList() {
        when(requestRepository.findHistory(eq(verifierId), anyCollection(),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(service.getHistory(verifierId, null, null, null, 0, 20)).isEmpty();
        verifyNoInteractions(requestVaultRepository);
    }

    @Test
    void getHistory_filterByStatus_passesOnlyThatStatusToQuery() {
        pendingRequest.setStatus(LegalVerificationStatus.REJECTED);
        when(requestRepository.findHistory(eq(verifierId), eq(List.of(LegalVerificationStatus.REJECTED)),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(pendingRequest));

        List<VerificationRequestResponseDto> result =
                service.getHistory(verifierId, LegalVerificationStatus.REJECTED, null, null, 0, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(LegalVerificationStatus.REJECTED);
    }

    @Test
    void getHistory_filterByDateRange_passesFullDayBoundsToQuery() {
        pendingRequest.setStatus(LegalVerificationStatus.APPROVED);
        pendingRequest.setDecidedAt(LocalDateTime.of(2026, 10, 5, 10, 0));
        // to = 31/10 tính trọn ngày -> cận trên là 00:00 ngày 01/11
        when(requestRepository.findHistory(eq(verifierId), anyCollection(),
                eq(LocalDateTime.of(2026, 10, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 11, 1, 0, 0)),
                any(Pageable.class)))
                .thenReturn(List.of(pendingRequest));

        List<VerificationRequestResponseDto> result = service.getHistory(
                verifierId, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 0, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDecidedAt()).isEqualTo(LocalDateTime.of(2026, 10, 5, 10, 0));
    }

    @Test
    void getHistory_invalidPageAndSize_areClamped() {
        // page < 0 -> 0; size > 100 -> 100
        when(requestRepository.findHistory(eq(verifierId), anyCollection(),
                any(LocalDateTime.class), any(LocalDateTime.class), eq(PageRequest.of(0, 100))))
                .thenReturn(List.of());

        assertThat(service.getHistory(verifierId, null, null, null, -5, 1000)).isEmpty();
    }

    @Test
    void getHistory_fromAfterTo_throwsInvalidDateRangeException() {
        LocalDate from = LocalDate.of(2026, 10, 10);
        LocalDate to = LocalDate.of(2026, 10, 5);

        assertThatThrownBy(() -> service.getHistory(verifierId, null, from, to, 0, 20))
                .isInstanceOf(InvalidDateRangeException.class);
    }
    // ---------- getDocument (D1) ----------

    @Test
    void getDocument_verifierIsOwner_throwsNotFoundAndNeverReadsFile() throws Exception {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.getDocument(requestId, ownerId))
                .isInstanceOf(VerificationRequestNotFoundException.class);

        verify(fileStorageService, never()).readFile(any());
    }

    @Test
    void getDocument_pending_returnsDecryptedBytesAndPdfContentType() throws Exception {
        byte[] cipher = "cipher-bytes".getBytes();
        byte[] plain = "%PDF-1.4 noi dung giay chung tu".getBytes();

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
        when(fileStorageService.readFile("enc://test.pdf")).thenReturn(cipher);
        when(cryptoService.decryptBytes(cipher)).thenReturn(plain);

        VerificationDocumentDto dto = service.getDocument(requestId, verifierId);

        assertThat(dto.getContent()).isEqualTo(plain);
        assertThat(dto.getContentType()).isEqualTo("application/pdf");
        assertThat(dto.getFileName()).endsWith(".pdf");
        verify(auditLogService).log(eq(AuditAction.LEGAL_DOCUMENT_VIEWED), eq(AuditResult.SUCCESS),
                eq(verifierId), any(), eq("LegalVerificationRequest"), eq(requestId.toString()), isNull());
    }

    @Test
    void getDocument_approvedByAnotherVerifier_throwsNotAssigned() throws Exception {
        pendingRequest.setStatus(LegalVerificationStatus.APPROVED);
        pendingRequest.setVerifier(User.builder().id(UUID.randomUUID()).build());
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.getDocument(requestId, verifierId))
                .isInstanceOf(NotAssignedVerifierException.class);

        verify(fileStorageService, never()).readFile(any());
    }
}