package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.dto.legalverificationdto.DocumentVerificationResponseDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.RejectVerificationRequestDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.SignVerificationRequestDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationDocumentDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationRequestResponseDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerifierSummaryResponseDto;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VerificationRequestVault;
import com.ltld.app.legacyvault.entity.VerificationReviewProgress;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.RejectionReason;
import com.ltld.app.legacyvault.enums.SignatureMethod;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.event.VerificationRequestDecidedEvent;
import com.ltld.app.legacyvault.exception.ConflictOfInterestException;
import com.ltld.app.legacyvault.exception.InvalidDateRangeException;
import com.ltld.app.legacyvault.exception.InvalidSignCodeException;
import com.ltld.app.legacyvault.exception.NotAssignedVerifierException;
import com.ltld.app.legacyvault.exception.RequestAlreadyReviewedException;
import com.ltld.app.legacyvault.exception.RequestNotApprovedException;
import com.ltld.app.legacyvault.exception.TooManyOtpRequestsException;
import com.ltld.app.legacyvault.exception.VaultStateConflictException;
import com.ltld.app.legacyvault.exception.VerificationRequestNotFoundException;
import com.ltld.app.legacyvault.exception.VerificationStepRequiredException;
import com.ltld.app.legacyvault.repository.DigitalSignatureRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
import com.ltld.app.legacyvault.repository.VerificationReviewProgressRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
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
import org.springframework.dao.DataIntegrityViolationException;
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

    @Mock
    private DmsConfigRepository dmsConfigRepository;

    @Mock
    private ExecutorAssignmentRepository executorAssignmentRepository;

    @Mock
    private VerifierAccessGuard verifierAccessGuard;

    @Mock
    private VerificationReviewProgressRepository progressRepository;

    @Mock
    private ReviewProgressRecorder progressRecorder;

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

    private VerificationReviewProgress progressOf(UUID verifier, boolean viewed, boolean checked, Boolean valid) {
        return VerificationReviewProgress.builder()
                .request(pendingRequest).verifierId(verifier)
                .viewedAt(viewed ? LocalDateTime.now() : null)
                .checkedAt(checked ? LocalDateTime.now() : null)
                .documentValid(valid)
                .build();
    }

    // FR-13: tiến độ của verifier hiện tại (bắt buộc dùng trong test, strict stubs)
    // Đã Check (hợp lệ) nhưng chưa View
    private VerificationReviewProgress checkedOnly() {
        VerificationReviewProgress p = progressOf(verifierId, false, true, true);
        when(progressRepository.findByRequestIdAndVerifierId(requestId, verifierId)).thenReturn(Optional.of(p));
        return p;
    }

    private VerificationReviewProgress viewedAndChecked() {
        VerificationReviewProgress p = progressOf(verifierId, true, true, true);
        when(progressRepository.findByRequestIdAndVerifierId(requestId, verifierId)).thenReturn(Optional.of(p));
        return p;
    }

    // Tiến độ của verifier KHÁC: có thể không được đọc tới trong test
    private void otherVerifierHasFullProgress(UUID other) {
        lenient().when(progressRepository.findByRequestIdAndVerifierId(requestId, other))
                .thenReturn(Optional.of(progressOf(other, true, true, true)));
    }

    private RejectVerificationRequestDto rejectDto(RejectionReason reason, String note) {
        RejectVerificationRequestDto dto = new RejectVerificationRequestDto();
        dto.setReason(reason);
        dto.setNote(note);
        return dto;
    }

    // ---------- reject ----------

    @Test
    void reject_success_setsStatusRejectedAndSavesReason() {
        RejectVerificationRequestDto dto = rejectDto(RejectionReason.DOCUMENT_INVALID, null);
        checkedOnly();

        User verifierUser = User.builder().id(verifierId).build();
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.getReferenceById(verifierId)).thenReturn(verifierUser);
        when(requestRepository.save(any(LegalVerificationRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.reject(requestId, verifierId, dto);

        assertThat(pendingRequest.getStatus()).isEqualTo(LegalVerificationStatus.REJECTED);
        assertThat(pendingRequest.getRejectionReasonCode()).isEqualTo(RejectionReason.DOCUMENT_INVALID);
        assertThat(pendingRequest.getRejectionReason()).isEqualTo(RejectionReason.DOCUMENT_INVALID.getLabel());
        assertThat(pendingRequest.getDecidedAt()).isNotNull();
        // FR-12: audit chỉ ghi mã lý do
        verify(auditLogService).log(eq(AuditAction.LEGAL_REQUEST_REJECTED), eq(AuditResult.SUCCESS),
                eq(verifierId), any(), eq("LegalVerificationRequest"), eq(requestId.toString()),
                eq("reason=DOCUMENT_INVALID"));
        // FR-12: phát event để gửi mail cho Executor
        verify(eventPublisher).publishEvent(new VerificationRequestDecidedEvent(
                requestId, LegalVerificationStatus.REJECTED, RejectionReason.DOCUMENT_INVALID.getLabel()));
    }

    // FR-12: chọn "Lý do khác" kèm ghi chú -> cột text lưu "label: note", audit không ghi note
    @Test
    void reject_otherWithNote_savesLabelAndNote_auditHasNoNote() {
        checkedOnly();
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.getReferenceById(verifierId)).thenReturn(User.builder().id(verifierId).build());
        when(requestRepository.save(any(LegalVerificationRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reject(requestId, verifierId, rejectDto(RejectionReason.OTHER, "  Ảnh bị cắt góc  "));

        assertThat(pendingRequest.getRejectionReasonCode()).isEqualTo(RejectionReason.OTHER);
        assertThat(pendingRequest.getRejectionReason()).isEqualTo("Other reason: Ảnh bị cắt góc");
        verify(auditLogService).log(eq(AuditAction.LEGAL_REQUEST_REJECTED), eq(AuditResult.SUCCESS),
                eq(verifierId), any(), eq("LegalVerificationRequest"), eq(requestId.toString()),
                eq("reason=OTHER"));
    }

    // FR-13: từ chối hồ sơ PENDING khi chưa Check -> 409
    @Test
    void reject_pendingNotChecked_throwsStepRequired() {
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.reject(requestId, verifierId,
                rejectDto(RejectionReason.DOCUMENT_INVALID, null)))
                .isInstanceOf(VerificationStepRequiredException.class);

        verify(requestRepository, never()).save(any());
    }

    // FR-13: người khác đã Check thì không tính cho verifier này
    @Test
    void reject_pendingCheckedByAnotherVerifier_throwsStepRequired() {
        otherVerifierHasFullProgress(UUID.randomUUID());
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.reject(requestId, verifierId,
                rejectDto(RejectionReason.DOCUMENT_INVALID, null)))
                .isInstanceOf(VerificationStepRequiredException.class);
    }

    // FR-12: verifier đã duyệt được từ chối hồ sơ APPROVED chưa ký
    @Test
    void reject_approvedBySameVerifier_rejectsAndClearsSignOtp() {
        approvedByVerifier();
        pendingRequest.setSignOtpHash("hash");
        pendingRequest.setSignAttempts(2);
        RejectVerificationRequestDto dto = rejectDto(RejectionReason.SUSPECTED_FORGERY, null);
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.getReferenceById(verifierId)).thenReturn(User.builder().id(verifierId).build());
        when(requestRepository.save(any(LegalVerificationRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.reject(requestId, verifierId, dto);

        assertThat(pendingRequest.getStatus()).isEqualTo(LegalVerificationStatus.REJECTED);
        assertThat(pendingRequest.getSignOtpHash()).isNull();
        assertThat(pendingRequest.getSignAttempts()).isZero();
        verify(eventPublisher).publishEvent(new VerificationRequestDecidedEvent(
                requestId, LegalVerificationStatus.REJECTED, RejectionReason.SUSPECTED_FORGERY.getLabel()));
    }

    // FR-12: verifier khác không được từ chối hồ sơ đã APPROVED
    @Test
    void reject_approvedByAnotherVerifier_throwsNotAssignedVerifier() {
        approvedByVerifier();
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.reject(requestId, UUID.randomUUID(), new RejectVerificationRequestDto()))
                .isInstanceOf(NotAssignedVerifierException.class);

        verify(requestRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void reject_requestNotFound_throwsNotFoundException() {
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reject(requestId, verifierId, new RejectVerificationRequestDto()))
                .isInstanceOf(VerificationRequestNotFoundException.class);

        verify(requestRepository, never()).save(any());
    }

    @Test
    void reject_alreadySigned_throwsAlreadyReviewedException() {
        pendingRequest.setStatus(LegalVerificationStatus.SIGNED);
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.reject(requestId, verifierId, new RejectVerificationRequestDto()))
                .isInstanceOf(RequestAlreadyReviewedException.class);

        verify(requestRepository, never()).save(any());
    }

    @Test
    void reject_verifierIsExecutor_throwsConflictOfInterest() {
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));
        RejectVerificationRequestDto dto = rejectDto(RejectionReason.OTHER, "test");

        assertThatThrownBy(() -> service.reject(requestId, pendingRequest.getExecutor().getId(), dto))
                .isInstanceOf(ConflictOfInterestException.class);

        verify(requestRepository, never()).save(any());
    }

    // ---------- approve (UC12) ----------

    @Test
    void approve_success_setsApprovedWithoutSigningOrUnlocking() {
        User verifierUser = User.builder().id(verifierId).email("verifier@test.com").build();
        viewedAndChecked();
        handoverStillValid();
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.getReferenceById(verifierId)).thenReturn(verifierUser);
        when(requestRepository.save(any(LegalVerificationRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.approve(requestId, verifierId);

        // FR-12: phát event để gửi mail cho Executor
        verify(eventPublisher).publishEvent(new VerificationRequestDecidedEvent(
                requestId, LegalVerificationStatus.APPROVED, null));

        assertThat(pendingRequest.getStatus()).isEqualTo(LegalVerificationStatus.APPROVED);
        assertThat(pendingRequest.getDecidedAt()).isNotNull();
        verifyNoInteractions(signatureRepository);
        // M3: audit phải có email người duyệt
        verify(auditLogService).log(eq(AuditAction.LEGAL_REQUEST_APPROVED), eq(AuditResult.SUCCESS),
                eq(verifierId), eq("verifier@test.com"),
                eq("LegalVerificationRequest"), eq(requestId.toString()), isNull());
    }

    // FR-14: DMS chưa TRIGGERED hoặc Executor không còn ACTIVE thì không được duyệt
    @Test
    void approve_handoverNoLongerValid_throwsConflictAndDoesNotSave() {
        viewedAndChecked();
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));
        when(dmsConfigRepository.findByOwnerId(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(requestId, verifierId))
                .isInstanceOf(VaultStateConflictException.class);

        verify(requestRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // FR-13: thiếu bước nào thì 409, không lưu gì
    @Test
    void approve_notChecked_throwsStepRequired() {
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.approve(requestId, verifierId))
                .isInstanceOf(VerificationStepRequiredException.class);
        verify(requestRepository, never()).save(any());
    }

    // Đã Check hợp lệ nhưng chưa View
    @Test
    void approve_checkedButNotViewed_throwsStepRequired() {
        checkedOnly();
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.approve(requestId, verifierId))
                .isInstanceOf(VerificationStepRequiredException.class);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void approve_checkedInvalid_throwsStepRequired() {
        viewedAndChecked().setDocumentValid(false);
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.approve(requestId, verifierId))
                .isInstanceOf(VerificationStepRequiredException.class);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void approve_viewedAndCheckedByAnotherVerifier_throwsStepRequired() {
        otherVerifierHasFullProgress(UUID.randomUUID());
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.approve(requestId, verifierId))
                .isInstanceOf(VerificationStepRequiredException.class);
    }

    // FR-13: B cũng đã View/Check thì A vẫn Approve được (tiến độ không đè lên nhau)
    @Test
    void approve_anotherVerifierAlsoProgressed_stillApprovesForMe() {
        viewedAndChecked();
        otherVerifierHasFullProgress(UUID.randomUUID());
        User verifierUser = User.builder().id(verifierId).email("verifier@test.com").build();
        handoverStillValid();
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.getReferenceById(verifierId)).thenReturn(verifierUser);
        when(requestRepository.save(any(LegalVerificationRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.approve(requestId, verifierId);

        assertThat(pendingRequest.getStatus()).isEqualTo(LegalVerificationStatus.APPROVED);
    }

    @Test
    void approve_alreadyRejected_throwsAlreadyReviewedException() {
        pendingRequest.setStatus(LegalVerificationStatus.REJECTED);
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.approve(requestId, verifierId))
                .isInstanceOf(RequestAlreadyReviewedException.class);

        verify(requestRepository, never()).save(any());
    }

    @Test
    void approve_verifierIsOwner_throwsConflictOfInterest() {
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));

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

    // D3: giả lập DMS của Owner đã TRIGGERED và Executor của hồ sơ còn ACTIVE
    private void handoverStillValid() {
        when(dmsConfigRepository.findByOwnerId(ownerId))
                .thenReturn(Optional.of(DmsConfig.builder().status(DmsStatus.TRIGGERED).build()));
        when(executorAssignmentRepository.existsByOwnerIdAndExecutorUserIdAndStatus(
                ownerId, pendingRequest.getExecutor().getId(), ExecutorStatus.ACTIVE)).thenReturn(true);
    }

    @Test
    void sign_success_savesSignatureUnlocksVaultAndSetsClaimDeadline() {
        approvedByVerifier();
        handoverStillValid();
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
        handoverStillValid();
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
        handoverStillValid();
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
        handoverStillValid();
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
        handoverStillValid();
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
        handoverStillValid();
        pendingRequest.setSignOtpSentAt(LocalDateTime.now().minusSeconds(10));
        User verifierUser = User.builder().id(verifierId).email("verifier@test.com").build();

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
        when(userRepository.findById(verifierId)).thenReturn(Optional.of(verifierUser));
        when(otpGenerator.generate()).thenReturn("123456");

        assertThatThrownBy(() -> service.sendSignOtp(requestId, verifierId))
                .isInstanceOf(TooManyOtpRequestsException.class);

        verifyNoInteractions(emailSender);
    }

    // n1: sendSignOtp cũng kiểm D3, không gửi mã cho hồ sơ không thể ký
    @Test
    void sendSignOtp_dmsNotTriggered_throwsConflictAndDoesNotSendEmail() {
        approvedByVerifier();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
        when(dmsConfigRepository.findByOwnerId(ownerId))
                .thenReturn(Optional.of(DmsConfig.builder().status(DmsStatus.ACTIVE).build()));

        assertThatThrownBy(() -> service.sendSignOtp(requestId, verifierId))
                .isInstanceOf(VaultStateConflictException.class);

        verifyNoInteractions(emailSender, otpGenerator, otpHasher);
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
        handoverStillValid();
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
        handoverStillValid();
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
        handoverStillValid();
        pendingRequest.setSignOtpHash("hashedOtp");
        pendingRequest.setSignOtpExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));
        when(otpHasher.matches(requestId, "000000", "hashedOtp")).thenReturn(true);
        when(requestVaultRepository.findByRequestIdFetchVault(requestId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.sign(requestId, verifierId, signDto()))
                .isInstanceOf(VaultStateConflictException.class);

        verify(signatureRepository, never()).save(any());
    }

    // ---------- D3: kiểm tra lại DMS và Executor khi ký ----------

    @Test
    void sign_dmsNotTriggered_throwsConflictAndDoesNotSignOrUnlock() {
        approvedByVerifier();
        pendingRequest.setSignOtpHash("hashedOtp");
        pendingRequest.setSignOtpExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));
        when(dmsConfigRepository.findByOwnerId(ownerId))
                .thenReturn(Optional.of(DmsConfig.builder().status(DmsStatus.ACTIVE).build()));

        assertThatThrownBy(() -> service.sign(requestId, verifierId, signDto()))
                .isInstanceOf(VaultStateConflictException.class);

        verify(signatureRepository, never()).save(any());
        verify(otpHasher, never()).matches(any(), any(), any());
        verifyNoInteractions(requestVaultRepository);
    }

    @Test
    void sign_noDmsConfig_throwsConflictAndDoesNotSignOrUnlock() {
        approvedByVerifier();
        pendingRequest.setSignOtpHash("hashedOtp");
        pendingRequest.setSignOtpExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));
        when(dmsConfigRepository.findByOwnerId(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sign(requestId, verifierId, signDto()))
                .isInstanceOf(VaultStateConflictException.class);

        verify(signatureRepository, never()).save(any());
        verify(otpHasher, never()).matches(any(), any(), any());
        verifyNoInteractions(requestVaultRepository);
    }

    @Test
    void sign_executorNoLongerActive_throwsConflictAndDoesNotSignOrUnlock() {
        approvedByVerifier();
        pendingRequest.setSignOtpHash("hashedOtp");
        pendingRequest.setSignOtpExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));
        when(dmsConfigRepository.findByOwnerId(ownerId))
                .thenReturn(Optional.of(DmsConfig.builder().status(DmsStatus.TRIGGERED).build()));
        when(executorAssignmentRepository.existsByOwnerIdAndExecutorUserIdAndStatus(
                ownerId, pendingRequest.getExecutor().getId(), ExecutorStatus.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> service.sign(requestId, verifierId, signDto()))
                .isInstanceOf(VaultStateConflictException.class);

        verify(signatureRepository, never()).save(any());
        verify(otpHasher, never()).matches(any(), any(), any());
        verifyNoInteractions(requestVaultRepository);
    }

    // ---------- UC13: verifyDocument + getById (quyền đọc do VerifierAccessGuard, test ở VerifierAccessGuardTest) ----------

    private void guardAllowsReading() {
        when(verifierAccessGuard.requireReadable(requestId, verifierId)).thenReturn(pendingRequest);
    }

    private void documentContains(byte[] plain) throws Exception {
        byte[] cipher = "cipher".getBytes();
        when(fileStorageService.readFile("enc://test.pdf")).thenReturn(cipher);
        when(cryptoService.decryptBytes(cipher)).thenReturn(plain);
    }

    // FR-13: Check là bước đầu, chưa có dòng tiến độ thì tạo mới (chưa cần View)
    @Test
    void verifyDocument_firstCheck_createsProgressRowWithoutView() throws Exception {
        guardAllowsReading();
        documentContains("%PDF-1.4 noi dung".getBytes());

        DocumentVerificationResponseDto result = service.verifyDocument(requestId, verifierId);

        assertThat(result.isValid()).isTrue();
        verify(progressRecorder).recordChecked(requestId, verifierId, true);
    }

    // FR-13: kiểm tra theo nội dung, tên file .pdf nhưng nội dung không phải PDF/ảnh -> không hợp lệ
    @Test
    void verifyDocument_fakeExtensionButTextContent_returnsInvalid() throws Exception {
        guardAllowsReading();
        documentContains("day chi la van ban".getBytes());

        assertThat(service.verifyDocument(requestId, verifierId).isValid()).isFalse();
        verify(progressRecorder).recordChecked(requestId, verifierId, false);
    }

    // FR-13: Check lại thì cập nhật dòng cũ, không tạo dòng mới
    @Test
    void verifyDocument_checkAgain_updatesExistingRow() throws Exception {
        VerificationReviewProgress progress = progressOf(verifierId, true, true, false);
        when(progressRepository.findByRequestIdAndVerifierId(requestId, verifierId)).thenReturn(Optional.of(progress));
        guardAllowsReading();
        documentContains("%PDF-1.4 noi dung".getBytes());

        assertThat(service.verifyDocument(requestId, verifierId).isValid()).isTrue();

        assertThat(progress.getDocumentValid()).isTrue();
        verify(progressRepository).save(progress);
        verify(progressRecorder, never()).recordChecked(any(), any(), anyBoolean());
    }

    // FR-13: tiến độ của người khác không ảnh hưởng, verifier này vẫn tạo dòng của mình
    @Test
    void verifyDocument_otherVerifierHasProgress_stillCreatesMine() throws Exception {
        otherVerifierHasFullProgress(UUID.randomUUID());
        guardAllowsReading();
        documentContains("%PDF-1.4".getBytes());

        service.verifyDocument(requestId, verifierId);

        verify(progressRecorder).recordChecked(requestId, verifierId, true);
    }

    // FR-13: hai request song song cùng tạo dòng -> bên thua (unique) không bị lỗi
    @Test
    void verifyDocument_parallelCreateConflict_stillReturnsResult() throws Exception {
        guardAllowsReading();
        documentContains("%PDF-1.4".getBytes());
        doThrow(new DataIntegrityViolationException("duplicate")).when(progressRecorder)
                .recordChecked(requestId, verifierId, true);

        assertThat(service.verifyDocument(requestId, verifierId).isValid()).isTrue();
    }

    @Test
    void verifyDocument_notPending_throwsAlreadyReviewed() {
        approvedByVerifier();
        guardAllowsReading();

        assertThatThrownBy(() -> service.verifyDocument(requestId, verifierId))
                .isInstanceOf(RequestAlreadyReviewedException.class);
    }

    @Test
    void verifyDocument_guardRejects_throws() {
        when(verifierAccessGuard.requireReadable(requestId, verifierId))
                .thenThrow(new VerificationRequestNotFoundException("not found"));

        assertThatThrownBy(() -> service.verifyDocument(requestId, verifierId))
                .isInstanceOf(VerificationRequestNotFoundException.class);
    }

    // FR-13: DTO cho FE chỉ tính tiến độ của chính verifier đang xem
    @Test
    void getById_progressIsPerVerifier() {
        viewedAndChecked();
        UUID other = UUID.randomUUID();
        when(verifierAccessGuard.requireReadable(requestId, verifierId)).thenReturn(pendingRequest);
        when(verifierAccessGuard.requireReadable(requestId, other)).thenReturn(pendingRequest);

        VerificationRequestResponseDto mine = service.getById(requestId, verifierId);
        VerificationRequestResponseDto theirs = service.getById(requestId, other);

        assertThat(mine.isDocumentViewed()).isTrue();
        assertThat(mine.isDocumentChecked()).isTrue();
        assertThat(mine.getDocumentValid()).isTrue();
        assertThat(theirs.isDocumentViewed()).isFalse();
        assertThat(theirs.isDocumentChecked()).isFalse();
        assertThat(theirs.getDocumentValid()).isNull();
    }

    @Test
    void getById_guardRejects_throwsAndLoadsNoVaults() {
        when(verifierAccessGuard.requireReadable(requestId, ownerId))
                .thenThrow(new VerificationRequestNotFoundException("not found"));

        assertThatThrownBy(() -> service.getById(requestId, ownerId))
                .isInstanceOf(VerificationRequestNotFoundException.class);

        verifyNoInteractions(requestVaultRepository);
    }

    @Test
    void getById_approvedByAnotherVerifier_propagatesNotAssigned() {
        when(verifierAccessGuard.requireReadable(requestId, verifierId))
                .thenThrow(new NotAssignedVerifierException("not assigned"));

        assertThatThrownBy(() -> service.getById(requestId, verifierId))
                .isInstanceOf(NotAssignedVerifierException.class);
    }

    // R1: toDto phải trả đúng createdAt
    @Test
    void getById_returnsCreatedAt() {
        pendingRequest.setCreatedAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        guardAllowsReading();

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
    // Luật quyền (owner/executor -> 404, đã xử lý -> chỉ verifier được giao) nằm ở VerifierAccessGuardTest

    @Test
    void getDocument_guardRejects_throwsAndNeverReadsFile() throws Exception {
        when(verifierAccessGuard.requireReadable(requestId, ownerId))
                .thenThrow(new VerificationRequestNotFoundException("not found"));

        assertThatThrownBy(() -> service.getDocument(requestId, ownerId))
                .isInstanceOf(VerificationRequestNotFoundException.class);

        verify(fileStorageService, never()).readFile(any());
    }

    @Test
    void getDocument_pendingAfterCheck_returnsDecryptedBytesAndRecordsView() throws Exception {
        VerificationReviewProgress progress = checkedOnly();
        byte[] cipher = "cipher-bytes".getBytes();
        byte[] plain = "%PDF-1.4 noi dung giay chung tu".getBytes();

        when(verifierAccessGuard.requireReadable(requestId, verifierId)).thenReturn(pendingRequest);
        when(fileStorageService.readFile("enc://test.pdf")).thenReturn(cipher);
        when(cryptoService.decryptBytes(cipher)).thenReturn(plain);

        VerificationDocumentDto dto = service.getDocument(requestId, verifierId);

        assertThat(dto.getContent()).isEqualTo(plain);
        assertThat(dto.getContentType()).isEqualTo("application/pdf");
        assertThat(dto.getFileName()).endsWith(".pdf");
        // FR-13: ghi nhận verifier đã xem (dòng riêng của verifier này)
        assertThat(progress.getViewedAt()).isNotNull();
        verify(progressRepository).save(progress);
        verify(auditLogService).log(eq(AuditAction.LEGAL_DOCUMENT_VIEWED), eq(AuditResult.SUCCESS),
                eq(verifierId), any(), eq("LegalVerificationRequest"), eq(requestId.toString()), isNull());
    }

    // FR-13: chưa Check thì không được View, và không đọc tệp
    @Test
    void getDocument_pendingNotChecked_throwsStepRequiredAndReadsNothing() throws Exception {
        when(verifierAccessGuard.requireReadable(requestId, verifierId)).thenReturn(pendingRequest);

        assertThatThrownBy(() -> service.getDocument(requestId, verifierId))
                .isInstanceOf(VerificationStepRequiredException.class);

        verify(fileStorageService, never()).readFile(any());
        verify(progressRepository, never()).save(any());
    }

    // FR-13: người khác đã Check thì không tính cho verifier này
    @Test
    void getDocument_pendingCheckedByAnotherVerifier_throwsStepRequired() throws Exception {
        otherVerifierHasFullProgress(UUID.randomUUID());
        when(verifierAccessGuard.requireReadable(requestId, verifierId)).thenReturn(pendingRequest);

        assertThatThrownBy(() -> service.getDocument(requestId, verifierId))
                .isInstanceOf(VerificationStepRequiredException.class);
    }

    // FR-13: Check không hợp lệ vẫn được View (để Verifier tự xem lý do)
    @Test
    void getDocument_checkedInvalid_stillAllowsView() throws Exception {
        VerificationReviewProgress progress = progressOf(verifierId, false, true, false);
        when(progressRepository.findByRequestIdAndVerifierId(requestId, verifierId)).thenReturn(Optional.of(progress));
        byte[] cipher = "c".getBytes();
        when(verifierAccessGuard.requireReadable(requestId, verifierId)).thenReturn(pendingRequest);
        when(fileStorageService.readFile("enc://test.pdf")).thenReturn(cipher);
        when(cryptoService.decryptBytes(cipher)).thenReturn("day chi la van ban".getBytes());

        service.getDocument(requestId, verifierId);

        assertThat(progress.getViewedAt()).isNotNull();
    }

    // FR-13: đã View rồi thì xem lại không ghi đè thời điểm cũ
    @Test
    void getDocument_alreadyViewed_keepsFirstViewTime() throws Exception {
        VerificationReviewProgress progress = viewedAndChecked();
        java.time.LocalDateTime firstView = progress.getViewedAt();
        byte[] cipher = "c".getBytes();
        when(verifierAccessGuard.requireReadable(requestId, verifierId)).thenReturn(pendingRequest);
        when(fileStorageService.readFile("enc://test.pdf")).thenReturn(cipher);
        when(cryptoService.decryptBytes(cipher)).thenReturn("%PDF-1.4".getBytes());

        service.getDocument(requestId, verifierId);

        assertThat(progress.getViewedAt()).isEqualTo(firstView);
        verify(progressRepository, never()).save(any());
    }

    // FR-13: xem lại hồ sơ đã APPROVED không ghi tiến độ và không cần Check
    @Test
    void getDocument_approved_doesNotTouchProgress() throws Exception {
        approvedByVerifier();
        byte[] cipher = "c".getBytes();
        when(verifierAccessGuard.requireReadable(requestId, verifierId)).thenReturn(pendingRequest);
        when(fileStorageService.readFile("enc://test.pdf")).thenReturn(cipher);
        when(cryptoService.decryptBytes(cipher)).thenReturn("%PDF-1.4".getBytes());

        service.getDocument(requestId, verifierId);

        verify(progressRepository, never()).save(any());
        verifyNoInteractions(progressRecorder);
    }

    // FR-14: nhập sai mã -> báo còn bao nhiêu lần thử
    @Test
    void sign_wrongCode_messageShowsRemainingAttempts() {
        approvedByVerifier();
        handoverStillValid();
        pendingRequest.setSignOtpHash("hashedOtp");
        pendingRequest.setSignOtpExpiresAt(LocalDateTime.now().plusMinutes(5));

        SignVerificationRequestDto badDto = new SignVerificationRequestDto();
        badDto.setMethod(SignatureMethod.MOCK_OTP);
        badDto.setCode("111111");

        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));
        when(otpHasher.matches(requestId, "111111", "hashedOtp")).thenReturn(false);

        assertThatThrownBy(() -> service.sign(requestId, verifierId, badDto))
                .isInstanceOf(InvalidSignCodeException.class)
                .hasMessageContaining("Attempts left: 4");
    }

    // FR-12: DTO trả cả tên vault (không chỉ UUID)
    @Test
    void getPendingRequests_includesVaultNames() {
        UUID vaultId = UUID.randomUUID();
        VerificationRequestVault link = VerificationRequestVault.builder()
                .request(pendingRequest).vault(Vault.builder().id(vaultId).name("Family vault").build()).build();

        when(requestRepository.findPendingVisibleTo(LegalVerificationStatus.PENDING, verifierId))
                .thenReturn(List.of(pendingRequest));
        when(requestVaultRepository.findByRequestIdIn(anyCollection())).thenReturn(List.of(link));

        VerificationRequestResponseDto dto = service.getPendingRequests(verifierId).get(0);

        assertThat(dto.getVaultIds()).containsExactly(vaultId);
        assertThat(dto.getVaults()).hasSize(1);
        assertThat(dto.getVaults().get(0).getId()).isEqualTo(vaultId);
        assertThat(dto.getVaults().get(0).getName()).isEqualTo("Family vault");
    }

    // FR-15: số liệu Home đếm trong DB, không phụ thuộc phân trang
    @Test
    void getSummary_countsEachStatusInDb() {
        when(requestRepository.countPendingVisibleTo(LegalVerificationStatus.PENDING, verifierId)).thenReturn(3L);
        when(requestRepository.countByVerifierIdAndStatus(verifierId, LegalVerificationStatus.APPROVED)).thenReturn(2L);
        when(requestRepository.countByVerifierIdAndStatus(verifierId, LegalVerificationStatus.SIGNED)).thenReturn(150L);
        when(requestRepository.countByVerifierIdAndStatus(verifierId, LegalVerificationStatus.REJECTED)).thenReturn(7L);

        VerifierSummaryResponseDto summary = service.getSummary(verifierId);

        assertThat(summary.getPending()).isEqualTo(3L);
        assertThat(summary.getAwaitingSignature()).isEqualTo(2L);
        assertThat(summary.getSigned()).isEqualTo(150L);
        assertThat(summary.getRejected()).isEqualTo(7L);
    }
}
