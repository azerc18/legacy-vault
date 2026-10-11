package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.AssetDownloadResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.CloseVaultPreviewResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.CloseVaultRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.CloseVaultResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.InheritedAssetDetailResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.InheritedAssetSummaryResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.VerifyIdentityRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.VerifyIdentityResponse;
import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.IdentityVerification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.enums.VerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import com.ltld.app.legacyvault.exception.BeneficiaryException;
import com.ltld.app.legacyvault.repository.AssetAccessRecordRepository;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.IdentityVerificationRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.utility.MockKycVerifier;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import com.ltld.app.legacyvault.utility.OtpHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BeneficiaryServiceImplTest {

    @Mock
    private VaultRepository vaultRepository;
    @Mock
    private BeneficiaryClaimRepository claimRepository;
    @Mock
    private IdentityVerificationRepository verificationRepository;
    @Mock
    private DigitalAssetRepository digitalAssetRepository;
    @Mock
    private CryptoService cryptoService;
    @Mock
    private MockKycVerifier mockKycVerifier;
    @Mock
    private EmailSender emailSender;
    @Mock
    private OtpGenerator otpGenerator;
    @Mock private AuditLogService auditLogService;
    @InjectMocks
    private BeneficiaryServiceImpl beneficiaryService;
    @Mock
    private AssetAccessRecordRepository assetAccessRecordRepository;

    private BeneficiaryClaimRequest request;
    private Vault unlockedVault;
    private User currentBeneficiary;
    private UUID vaultId;
    private UUID currentUserId;
    @Spy
    private OtpHasher otpHasher = new OtpHasher();

    @BeforeEach
    void setUp() {
        vaultId = UUID.randomUUID();
        currentUserId = UUID.randomUUID();

        request = new BeneficiaryClaimRequest();
        request.setVaultId(vaultId);
        request.setVerificationMethod(VerificationMethod.OTP);

        currentBeneficiary = new User();
        currentBeneficiary.setId(currentUserId);

        unlockedVault = new Vault();
        unlockedVault.setId(vaultId);
        unlockedVault.setBeneficiary(currentBeneficiary);
        unlockedVault.setStatus(VaultStatus.UNLOCKED);
    }

    private BeneficiaryClaim claim(ClaimStatus status, LocalDateTime deadline) {
        return BeneficiaryClaim.builder()
                .id(UUID.randomUUID()).vault(unlockedVault)
                .status(status).claimDeadlineAt(deadline).build();
    }

    // ===================== FR-17: helper =====================

    private IdentityVerification pendingSession(VerificationMethod method) {
        return IdentityVerification.builder()
                .id(UUID.randomUUID())
                .beneficiary(currentBeneficiary)
                .vault(unlockedVault)
                .method(method)
                .status(VerificationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private VerifyIdentityRequest verifyRequest(VerificationMethod method, String otp, String kycIdNumber) {
        VerifyIdentityRequest r = new VerifyIdentityRequest();
        r.setVaultId(vaultId);
        r.setVerificationMethod(method);
        r.setOtp(otp);
        r.setKycIdNumber(kycIdNumber);
        return r;
    }

    private DigitalAsset asset(UUID id, String name, AssetType type) {
        return DigitalAsset.builder()
                .id(id)
                .vault(unlockedVault)
                .assetType(type)
                .assetName(name)
                .encryptedSecret("enc-secret")
                .encryptionKeyRef("default-master-key")
                .notesEncrypted("enc-notes")
                .attachmentUrl("https://example.com/file.pdf")
                .status(AssetStatus.ACTIVE)
                .build();
    }

    // Vault UNLOCKED + claim còn hạn
    private void stubVaultAndClaim() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.of(
                claim(ClaimStatus.PENDING, LocalDateTime.now().plusDays(5))));
    }

    private void stubLocked(boolean locked) {
        when(verificationRepository.existsByVaultIdAndBeneficiaryIdAndStatus(
                vaultId, currentUserId, VerificationStatus.FAILED)).thenReturn(locked);
    }

    // Vault hợp lệ, claim còn hạn, chưa bị khóa
    private void stubAccessibleVault() {
        stubVaultAndClaim();
        stubLocked(false);
    }

    private void stubPendingSession(IdentityVerification session) {
        when(verificationRepository
                .findFirstByVaultIdAndBeneficiaryIdAndMethodAndStatusOrderByCreatedAtDesc(
                        vaultId, currentUserId, session.getMethod(), VerificationStatus.PENDING))
                .thenReturn(Optional.of(session));
    }

    private void stubViewSession(boolean valid) {
        when(verificationRepository.existsByVaultIdAndBeneficiaryIdAndStatusAndVerifiedAtAfter(
                eq(vaultId), eq(currentUserId), eq(VerificationStatus.SUCCESS), any(LocalDateTime.class)))
                .thenReturn(valid);
    }

    private BeneficiaryException expectError(HttpStatus expected, Executable action) {
        BeneficiaryException ex = assertThrows(BeneficiaryException.class, action);
        assertEquals(expected, ex.getStatus());
        return ex;
    }

    @Test
    void initializeClaim_wrongBeneficiary_throwsBeneficiaryException_404() {
        // Giả lập Vault thuộc về một người khác
        User differentUser = new User();
        differentUser.setId(UUID.randomUUID());
        unlockedVault.setBeneficiary(differentUser);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));

        BeneficiaryException ex = assertThrows(BeneficiaryException.class, () ->
                beneficiaryService.initializeClaim(request, currentUserId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Không tìm thấy yêu cầu nhận tài sản.", ex.getMessage());
    }

    @Test
    void initializeClaim_vaultNotUnlocked_throwsBeneficiaryException_400() {
        unlockedVault.setStatus(VaultStatus.ACTIVE); // Vault chưa sẵn sàng
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));

        BeneficiaryException ex = assertThrows(BeneficiaryException.class, () ->
                beneficiaryService.initializeClaim(request, currentUserId));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Tài sản chưa sẵn sàng để nhận.", ex.getMessage());
    }

    @Test
    void initializeClaim_success_reusesExistingPendingVerification() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));

        // Giả lập đã có sẵn Claim
        BeneficiaryClaim existingClaim = BeneficiaryClaim.builder()
                .id(UUID.randomUUID())
                .vault(unlockedVault)
                .status(ClaimStatus.PENDING)
                .claimDeadlineAt(LocalDateTime.now().plusDays(5))
                .build();
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.of(existingClaim));

        // Giả lập đã có sẵn 1 phiên xác thực PENDING
        IdentityVerification pendingVerification = IdentityVerification.builder()
                .id(UUID.randomUUID())
                .vault(unlockedVault)
                .status(VerificationStatus.PENDING)
                .build();
        when(verificationRepository.findFirstByVaultIdAndBeneficiaryIdAndMethodAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                any(), any(), any(), any(), any())).thenReturn(Optional.of(pendingVerification));
        BeneficiaryClaimResponse response = beneficiaryService.initializeClaim(request, currentUserId);

        // Kiểm tra xem hàm save của verification có KHÔNG bị gọi thêm lần nào (tái sử dụng thành công)
        verify(verificationRepository, never()).save(any());
        assertThat(response.getVerificationId()).isEqualTo(pendingVerification.getId());
    }

    @Test
    void initializeClaim_vaultNotFound_throws404() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.empty());

        BeneficiaryException ex = assertThrows(BeneficiaryException.class,
                () -> beneficiaryService.initializeClaim(request, currentUserId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void initializeClaim_vaultHasNoBeneficiary_throws404() {
        unlockedVault.setBeneficiary(null);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));

        BeneficiaryException ex = assertThrows(BeneficiaryException.class,
                () -> beneficiaryService.initializeClaim(request, currentUserId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void initializeClaim_alreadyClaimed_throws409() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));
        when(claimRepository.findByVaultId(vaultId))
                .thenReturn(Optional.of(claim(ClaimStatus.CLAIMED, LocalDateTime.now().plusDays(5))));

        BeneficiaryException ex = assertThrows(BeneficiaryException.class,
                () -> beneficiaryService.initializeClaim(request, currentUserId));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    void initializeClaim_statusExpired_throws410() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));
        when(claimRepository.findByVaultId(vaultId))
                .thenReturn(Optional.of(claim(ClaimStatus.EXPIRED, LocalDateTime.now().plusDays(5))));

        BeneficiaryException ex = assertThrows(BeneficiaryException.class,
                () -> beneficiaryService.initializeClaim(request, currentUserId));

        assertEquals(HttpStatus.GONE, ex.getStatus());
    }

    @Test
    void initializeClaim_deadlinePassedButStatusPending_throws410() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));
        when(claimRepository.findByVaultId(vaultId))
                .thenReturn(Optional.of(claim(ClaimStatus.PENDING, LocalDateTime.now().minusMinutes(1))));

        BeneficiaryException ex = assertThrows(BeneficiaryException.class,
                () -> beneficiaryService.initializeClaim(request, currentUserId));

        assertEquals(HttpStatus.GONE, ex.getStatus());
    }

    @Test
    void initializeClaim_noClaim_createsClaimWithVaultDeadlineAndNewVerification() {
        LocalDateTime deadline = LocalDateTime.now().plusDays(10);
        unlockedVault.setClaimDeadlineAt(deadline);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.empty());
        when(claimRepository.save(any(BeneficiaryClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(verificationRepository.findFirstByVaultIdAndBeneficiaryIdAndMethodAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(verificationRepository.save(any(IdentityVerification.class))).thenAnswer(inv -> inv.getArgument(0));

        beneficiaryService.initializeClaim(request, currentUserId);

        ArgumentCaptor<BeneficiaryClaim> claimCaptor = ArgumentCaptor.forClass(BeneficiaryClaim.class);
        verify(claimRepository).save(claimCaptor.capture());
        assertThat(claimCaptor.getValue().getStatus()).isEqualTo(ClaimStatus.PENDING);
        assertThat(claimCaptor.getValue().getClaimDeadlineAt()).isEqualTo(deadline);
        assertThat(claimCaptor.getValue().getBeneficiary()).isSameAs(currentBeneficiary);

        ArgumentCaptor<IdentityVerification> verCaptor = ArgumentCaptor.forClass(IdentityVerification.class);
        verify(verificationRepository).save(verCaptor.capture());
        assertThat(verCaptor.getValue().getStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(verCaptor.getValue().getMethod()).isEqualTo(VerificationMethod.OTP);
        assertThat(verCaptor.getValue().getVault()).isSameAs(unlockedVault);
    }

    @Test
    void initializeClaim_noClaimAndNoVaultDeadline_deadlineIsUnlockedAtPlus60Days() {
        LocalDateTime unlockedAt = LocalDateTime.now().minusDays(10);
        unlockedVault.setUnlockedAt(unlockedAt);
        unlockedVault.setClaimDeadlineAt(null);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.empty());
        when(claimRepository.save(any(BeneficiaryClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(verificationRepository.findFirstByVaultIdAndBeneficiaryIdAndMethodAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(verificationRepository.save(any(IdentityVerification.class))).thenAnswer(inv -> inv.getArgument(0));

        beneficiaryService.initializeClaim(request, currentUserId);

        ArgumentCaptor<BeneficiaryClaim> claimCaptor = ArgumentCaptor.forClass(BeneficiaryClaim.class);
        verify(claimRepository).save(claimCaptor.capture());
        assertThat(claimCaptor.getValue().getClaimDeadlineAt()).isEqualTo(unlockedAt.plusDays(60));
    }

    @Test
    void initializeClaim_noClaimButVaultUnlockedOver60DaysAgo_throws410() {
        unlockedVault.setUnlockedAt(LocalDateTime.now().minusDays(70));
        unlockedVault.setClaimDeadlineAt(null);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.empty());
        when(claimRepository.save(any(BeneficiaryClaim.class))).thenAnswer(inv -> inv.getArgument(0));

        BeneficiaryException ex = assertThrows(BeneficiaryException.class,
                () -> beneficiaryService.initializeClaim(request, currentUserId));

        assertEquals(HttpStatus.GONE, ex.getStatus());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    void initializeClaim_vaultAlreadyClaimed_throws409() {
        unlockedVault.setStatus(VaultStatus.CLAIMED);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));

        BeneficiaryException ex = assertThrows(BeneficiaryException.class,
                () -> beneficiaryService.initializeClaim(request, currentUserId));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    void initializeClaim_vaultArchivedLocked_throws410() {
        unlockedVault.setStatus(VaultStatus.ARCHIVED_LOCKED);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));

        BeneficiaryException ex = assertThrows(BeneficiaryException.class,
                () -> beneficiaryService.initializeClaim(request, currentUserId));

        assertEquals(HttpStatus.GONE, ex.getStatus());
    }


    @Test
    void initializeClaim_differentMethodThanPendingSession_createsNewVerification() {
        when(mockKycVerifier.isEnabled()).thenReturn(true);
        request.setVerificationMethod(VerificationMethod.EKYC_MOCK);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.of(
                claim(ClaimStatus.PENDING, LocalDateTime.now().plusDays(5))));
        when(verificationRepository
                .findFirstByVaultIdAndBeneficiaryIdAndMethodAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                        any(), any(), eq(VerificationMethod.EKYC_MOCK), any(), any()))
                .thenReturn(Optional.empty());
        when(verificationRepository.save(any(IdentityVerification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        beneficiaryService.initializeClaim(request, currentUserId);

        ArgumentCaptor<IdentityVerification> captor = ArgumentCaptor.forClass(IdentityVerification.class);
        verify(verificationRepository).save(captor.capture());
        assertThat(captor.getValue().getMethod()).isEqualTo(VerificationMethod.EKYC_MOCK);
    }

    // ===================== FR-17: gửi OTP =====================


    @Test
    void sendIdentityOtp_methodIsNotOtp_throws400() {
        request.setVerificationMethod(VerificationMethod.EKYC_MOCK);
        stubAccessibleVault();

        expectError(HttpStatus.BAD_REQUEST,
                () -> beneficiaryService.sendIdentityOtp(request, currentUserId));

        verify(emailSender, never()).sendEmail(any(), any());
    }

    @Test
    void sendIdentityOtp_noPendingSession_throws400() {
        stubAccessibleVault();
        when(verificationRepository
                .findFirstByVaultIdAndBeneficiaryIdAndMethodAndStatusOrderByCreatedAtDesc(
                        any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        expectError(HttpStatus.BAD_REQUEST,
                () -> beneficiaryService.sendIdentityOtp(request, currentUserId));
    }

    @Test
    void sendIdentityOtp_locked_throws423() {
        stubVaultAndClaim();
        stubLocked(true);

        expectError(HttpStatus.LOCKED,
                () -> beneficiaryService.sendIdentityOtp(request, currentUserId));

        verify(emailSender, never()).sendEmail(any(), any());
    }

    @Test
    void sendIdentityOtp_wrongBeneficiary_throws404() {
        User other = new User();
        other.setId(UUID.randomUUID());
        unlockedVault.setBeneficiary(other);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));

        expectError(HttpStatus.NOT_FOUND,
                () -> beneficiaryService.sendIdentityOtp(request, currentUserId));
    }

    @Test
    void sendIdentityOtp_vaultNotUnlocked_throws400() {
        unlockedVault.setStatus(VaultStatus.ACTIVE);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));

        expectError(HttpStatus.BAD_REQUEST,
                () -> beneficiaryService.sendIdentityOtp(request, currentUserId));
    }

    @Test
    void sendIdentityOtp_noClaimYet_throws400() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.empty());

        expectError(HttpStatus.BAD_REQUEST,
                () -> beneficiaryService.sendIdentityOtp(request, currentUserId));
    }

    @Test
    void sendIdentityOtp_claimPastDeadline_throws410() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.of(
                claim(ClaimStatus.PENDING, LocalDateTime.now().minusMinutes(1))));

        expectError(HttpStatus.GONE,
                () -> beneficiaryService.sendIdentityOtp(request, currentUserId));
    }

    // ===================== FR-17: xác thực danh tính =====================


    @Test
    void verifyIdentity_expiredOtp_throws400WithoutCountingAttempt() {
        IdentityVerification session = pendingSession(VerificationMethod.OTP);
        session.setOtpCode("123456");
        session.setOtpExpiresAt(LocalDateTime.now().minusSeconds(1));
        stubAccessibleVault();
        stubPendingSession(session);

        expectError(HttpStatus.BAD_REQUEST,
                () -> beneficiaryService.verifyIdentity(
                        verifyRequest(VerificationMethod.OTP, "123456", null), currentUserId));

        verify(verificationRepository, never()).save(any());
        verify(verificationRepository, never()).incrementAttemptCount(any());
    }

    @Test
    void verifyIdentity_otpNeverSent_throws400() {
        IdentityVerification session = pendingSession(VerificationMethod.OTP);
        stubAccessibleVault();
        stubPendingSession(session);

        expectError(HttpStatus.BAD_REQUEST,
                () -> beneficiaryService.verifyIdentity(
                        verifyRequest(VerificationMethod.OTP, "123456", null), currentUserId));

        verify(verificationRepository, never()).save(any());
        verify(verificationRepository, never()).incrementAttemptCount(any());
    }

    @Test
    void verifyIdentity_otpMissingInRequest_throws400() {
        IdentityVerification session = pendingSession(VerificationMethod.OTP);
        stubAccessibleVault();
        stubPendingSession(session);

        expectError(HttpStatus.BAD_REQUEST,
                () -> beneficiaryService.verifyIdentity(
                        verifyRequest(VerificationMethod.OTP, null, null), currentUserId));
        verify(verificationRepository, never()).incrementAttemptCount(any());
    }


    @Test
    void verifyIdentity_ekycIdMissing_throws400() {
        when(mockKycVerifier.isEnabled()).thenReturn(true);
        IdentityVerification session = pendingSession(VerificationMethod.EKYC_MOCK);
        stubAccessibleVault();
        stubPendingSession(session);

        expectError(HttpStatus.BAD_REQUEST,
                () -> beneficiaryService.verifyIdentity(
                        verifyRequest(VerificationMethod.EKYC_MOCK, null, null), currentUserId));

        verify(mockKycVerifier, never()).verify(any());
        verify(verificationRepository, never()).incrementAttemptCount(any());
    }

    @Test
    void verifyIdentity_locked_throws423() {
        stubVaultAndClaim();
        stubLocked(true);

        expectError(HttpStatus.LOCKED,
                () -> beneficiaryService.verifyIdentity(
                        verifyRequest(VerificationMethod.OTP, "123456", null), currentUserId));

        verify(verificationRepository, never()).save(any());
    }

    // ===================== FR-17: xem tài sản =====================

    @Test
    void getInheritedAssets_withViewSession_returnsSummariesWithoutDecrypting() {
        stubAccessibleVault();
        stubViewSession(true);
        when(digitalAssetRepository.findByVaultIdAndStatus(vaultId, AssetStatus.ACTIVE)).thenReturn(List.of(
                asset(UUID.randomUUID(), "Vietcombank", AssetType.BANK_ACCOUNT),
                asset(UUID.randomUUID(), "Ví Binance", AssetType.CRYPTO_WALLET)));

        List<InheritedAssetSummaryResponse> result =
                beneficiaryService.getInheritedAssets(vaultId, currentUserId);

        assertThat(result).extracting(InheritedAssetSummaryResponse::getAssetName)
                .containsExactly("Vietcombank", "Ví Binance");
        verifyNoInteractions(cryptoService); // danh sách không giải mã secret
    }

    @Test
    void getInheritedAssets_noViewSession_throws403() {
        stubAccessibleVault();
        stubViewSession(false);

        expectError(HttpStatus.FORBIDDEN,
                () -> beneficiaryService.getInheritedAssets(vaultId, currentUserId));

        verifyNoInteractions(digitalAssetRepository);
    }

    @Test
    void getInheritedAssets_locked_throws423() {
        stubVaultAndClaim();
        stubLocked(true);

        expectError(HttpStatus.LOCKED,
                () -> beneficiaryService.getInheritedAssets(vaultId, currentUserId));

        verifyNoInteractions(digitalAssetRepository);
    }

    @Test
    void getInheritedAssetDetail_withViewSession_returnsDecryptedData() throws Exception {
        UUID assetId = UUID.randomUUID();
        stubAccessibleVault();
        stubViewSession(true);
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.of(asset(assetId, "Vietcombank", AssetType.BANK_ACCOUNT)));
        when(cryptoService.decrypt("enc-secret")).thenReturn("my-secret");
        when(cryptoService.decrypt("enc-notes")).thenReturn("my-notes");

        InheritedAssetDetailResponse detail =
                beneficiaryService.getInheritedAssetDetail(vaultId, assetId, currentUserId);

        assertThat(detail.getId()).isEqualTo(assetId);
        assertThat(detail.getSecret()).isEqualTo("my-secret");
        assertThat(detail.getNotes()).isEqualTo("my-notes");

    }

    @Test
    void getInheritedAssetDetail_assetNotInVault_throws404() {
        UUID assetId = UUID.randomUUID();
        stubAccessibleVault();
        stubViewSession(true);
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.empty());

        expectError(HttpStatus.NOT_FOUND,
                () -> beneficiaryService.getInheritedAssetDetail(vaultId, assetId, currentUserId));

        verifyNoInteractions(cryptoService);
    }

    @Test
    void getInheritedAssetDetail_decryptFails_throws500WithoutLeakingData() throws Exception {
        UUID assetId = UUID.randomUUID();
        stubAccessibleVault();
        stubViewSession(true);
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.of(asset(assetId, "Vietcombank", AssetType.BANK_ACCOUNT)));
        when(cryptoService.decrypt(any())).thenThrow(new RuntimeException("boom"));

        BeneficiaryException ex = expectError(HttpStatus.INTERNAL_SERVER_ERROR,
                () -> beneficiaryService.getInheritedAssetDetail(vaultId, assetId, currentUserId));

        assertThat(ex.getMessage()).doesNotContain("enc-secret").doesNotContain("boom");
    }

    @Test
    void getInheritedAssetDetail_noViewSession_throws403() {
        UUID assetId = UUID.randomUUID();
        stubAccessibleVault();
        stubViewSession(false);

        expectError(HttpStatus.FORBIDDEN,
                () -> beneficiaryService.getInheritedAssetDetail(vaultId, assetId, currentUserId));

        verifyNoInteractions(digitalAssetRepository);
        verifyNoInteractions(cryptoService);
    }

    @Test
    void sendIdentityOtp_success_storesOnlyHashAndSendsPlainOtpByEmail() {
        currentBeneficiary.setEmail("beneficiary@example.com");
        IdentityVerification session = pendingSession(VerificationMethod.OTP);
        stubAccessibleVault();
        stubPendingSession(session);
        when(otpGenerator.generate()).thenReturn("123456");

        beneficiaryService.sendIdentityOtp(request, currentUserId);

        ArgumentCaptor<IdentityVerification> captor = ArgumentCaptor.forClass(IdentityVerification.class);
        verify(verificationRepository).save(captor.capture());
        IdentityVerification saved = captor.getValue();
        assertThat(saved.getOtpCode()).isNotEqualTo("123456");
        assertThat(saved.getOtpCode()).isEqualTo(otpHasher.hash(session.getId(), "123456"));
        assertThat(saved.getOtpSentAt()).isNotNull();
        assertThat(saved.getOtpExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(4));
        verify(emailSender).sendEmail("beneficiary@example.com", "123456");
    }

    @Test
    void sendIdentityOtp_resendTooSoon_throws429() {
        IdentityVerification session = pendingSession(VerificationMethod.OTP);
        session.setOtpCode("some-hash");
        session.setOtpSentAt(LocalDateTime.now());
        session.setOtpExpiresAt(LocalDateTime.now().plusMinutes(5));
        stubAccessibleVault();
        stubPendingSession(session);

        expectError(HttpStatus.TOO_MANY_REQUESTS,
                () -> beneficiaryService.sendIdentityOtp(request, currentUserId));

        verify(emailSender, never()).sendEmail(any(), any());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    void sendIdentityOtp_resendAfterWindow_sendsNewOtpWithoutResettingAttempts() {
        currentBeneficiary.setEmail("beneficiary@example.com");
        IdentityVerification session = pendingSession(VerificationMethod.OTP);
        session.setOtpCode(otpHasher.hash(session.getId(), "111111"));
        session.setOtpSentAt(LocalDateTime.now().minusMinutes(2));
        session.setOtpExpiresAt(LocalDateTime.now().plusMinutes(3));
        session.setAttemptCount(2);
        stubAccessibleVault();
        stubPendingSession(session);
        when(otpGenerator.generate()).thenReturn("654321");

        beneficiaryService.sendIdentityOtp(request, currentUserId);

        ArgumentCaptor<IdentityVerification> captor = ArgumentCaptor.forClass(IdentityVerification.class);
        verify(verificationRepository).save(captor.capture());
        assertThat(captor.getValue().getOtpCode()).isEqualTo(otpHasher.hash(session.getId(), "654321"));
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(2);
        verify(emailSender).sendEmail("beneficiary@example.com", "654321");
    }

    @Test
    void verifyIdentity_correctOtp_marksSuccessAndOpensViewSession() {
        IdentityVerification session = pendingSession(VerificationMethod.OTP);
        session.setOtpCode(otpHasher.hash(session.getId(), "123456"));
        session.setOtpExpiresAt(LocalDateTime.now().plusMinutes(3));
        stubAccessibleVault();
        stubPendingSession(session);
        when(verificationRepository.findAttemptCountById(session.getId())).thenReturn(1);

        VerifyIdentityResponse response = beneficiaryService.verifyIdentity(
                verifyRequest(VerificationMethod.OTP, "123456", null), currentUserId);

        assertThat(response.getStatus()).isEqualTo(VerificationStatus.SUCCESS);
        assertThat(response.getVerifiedAt()).isNotNull();
        assertThat(response.getViewSessionExpiresAt()).isAfter(response.getVerifiedAt());

        ArgumentCaptor<IdentityVerification> captor = ArgumentCaptor.forClass(IdentityVerification.class);
        verify(verificationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(VerificationStatus.SUCCESS);
        assertThat(captor.getValue().getOtpCode()).isNull(); // OTP không dùng lại được
    }

    @Test
    void verifyIdentity_wrongOtp_reservesAttemptAtomicallyAndStaysPending() {
        IdentityVerification session = pendingSession(VerificationMethod.OTP);
        session.setOtpCode(otpHasher.hash(session.getId(), "123456"));
        session.setOtpExpiresAt(LocalDateTime.now().plusMinutes(3));
        stubAccessibleVault();
        stubPendingSession(session);
        when(verificationRepository.findAttemptCountById(session.getId())).thenReturn(1);

        BeneficiaryException ex = expectError(HttpStatus.BAD_REQUEST,
                () -> beneficiaryService.verifyIdentity(
                        verifyRequest(VerificationMethod.OTP, "000000", null), currentUserId));

        assertThat(ex.getMessage()).contains("còn 4 lần"); // còn 4 lần thử
        // Bộ đếm tăng trong DB bằng một câu UPDATE, không tính trên RAM
        verify(verificationRepository).incrementAttemptCount(session.getId());
        assertThat(session.getAttemptCount()).isEqualTo(1);
        assertThat(session.getStatus()).isEqualTo(VerificationStatus.PENDING);
    }

    @Test
    void verifyIdentity_fifthWrongAttempt_locksSession() {
        IdentityVerification session = pendingSession(VerificationMethod.OTP);
        session.setOtpCode(otpHasher.hash(session.getId(), "123456"));
        session.setOtpExpiresAt(LocalDateTime.now().plusMinutes(3));
        stubAccessibleVault();
        stubPendingSession(session);
        when(verificationRepository.findAttemptCountById(session.getId())).thenReturn(5);

        expectError(HttpStatus.LOCKED,
                () -> beneficiaryService.verifyIdentity(
                        verifyRequest(VerificationMethod.OTP, "000000", null), currentUserId));

        ArgumentCaptor<IdentityVerification> captor = ArgumentCaptor.forClass(IdentityVerification.class);
        verify(verificationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(VerificationStatus.FAILED);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(5);
        assertThat(captor.getValue().getOtpCode()).isNull();
    }

    @Test
    void verifyIdentity_attemptBeyondLimit_locksWithoutCheckingTheCode() {
        IdentityVerification session = pendingSession(VerificationMethod.OTP);
        session.setOtpCode(otpHasher.hash(session.getId(), "123456"));
        session.setOtpExpiresAt(LocalDateTime.now().plusMinutes(3));
        stubAccessibleVault();
        stubPendingSession(session);
        // Request thứ 6 chen vào lúc phiên chưa kịp bị khóa
        when(verificationRepository.findAttemptCountById(session.getId())).thenReturn(6);

        // Dù gửi OTP ĐÚNG vẫn bị từ chối vì đã hết lượt
        expectError(HttpStatus.LOCKED,
                () -> beneficiaryService.verifyIdentity(
                        verifyRequest(VerificationMethod.OTP, "123456", null), currentUserId));

        verify(otpHasher, never()).matches(any(), any(), any());
        assertThat(session.getStatus()).isEqualTo(VerificationStatus.FAILED);
    }

    @Test
    void verifyIdentity_ekycValid_marksSuccess() {
        when(mockKycVerifier.isEnabled()).thenReturn(true);
        IdentityVerification session = pendingSession(VerificationMethod.EKYC_MOCK);
        stubAccessibleVault();
        stubPendingSession(session);
        when(verificationRepository.findAttemptCountById(session.getId())).thenReturn(1);
        when(mockKycVerifier.verify("123456789012")).thenReturn(true);

        VerifyIdentityResponse response = beneficiaryService.verifyIdentity(
                verifyRequest(VerificationMethod.EKYC_MOCK, null, "123456789012"), currentUserId);

        assertThat(response.getStatus()).isEqualTo(VerificationStatus.SUCCESS);
    }

    @Test
    void verifyIdentity_ekycInvalid_reservesAttempt() {
        when(mockKycVerifier.isEnabled()).thenReturn(true);
        IdentityVerification session = pendingSession(VerificationMethod.EKYC_MOCK);
        stubAccessibleVault();
        stubPendingSession(session);
        when(verificationRepository.findAttemptCountById(session.getId())).thenReturn(1);
        when(mockKycVerifier.verify("123")).thenReturn(false);

        expectError(HttpStatus.BAD_REQUEST,
                () -> beneficiaryService.verifyIdentity(
                        verifyRequest(VerificationMethod.EKYC_MOCK, null, "123"), currentUserId));

        verify(verificationRepository).incrementAttemptCount(session.getId());
        assertThat(session.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void verifyIdentity_attemptsFromOtherSessionsCount_locksWhenTotalReachesLimit() {
        IdentityVerification session = pendingSession(VerificationMethod.OTP);
        session.setOtpCode(otpHasher.hash(session.getId(), "123456"));
        session.setOtpExpiresAt(LocalDateTime.now().plusMinutes(3));
        stubAccessibleVault();
        stubPendingSession(session);
        when(verificationRepository.findAttemptCountById(session.getId())).thenReturn(1);
        // Các phiên PENDING cũ đã có 4 lần sai, cộng 1 lần này là 5
        when(verificationRepository.sumAttemptsOfOtherPendingSessions(
                eq(vaultId), eq(currentUserId), eq(session.getId()), any(LocalDateTime.class)))
                .thenReturn(4);

        expectError(HttpStatus.LOCKED,
                () -> beneficiaryService.verifyIdentity(
                        verifyRequest(VerificationMethod.OTP, "000000", null), currentUserId));

        assertThat(session.getStatus()).isEqualTo(VerificationStatus.FAILED);
    }

    @Test
    void verifyIdentity_pendingSessionOlderThanTtl_throws400() {
        IdentityVerification session = pendingSession(VerificationMethod.OTP);
        session.setCreatedAt(LocalDateTime.now().minusMinutes(16));
        stubAccessibleVault();
        stubPendingSession(session);

        expectError(HttpStatus.BAD_REQUEST,
                () -> beneficiaryService.verifyIdentity(
                        verifyRequest(VerificationMethod.OTP, "123456", null), currentUserId));

        verify(verificationRepository, never()).incrementAttemptCount(any());
    }

    @Test
    void verifyIdentity_ekycWhenMockDisabled_throws400_andNoAttemptCounted() {
        IdentityVerification session = pendingSession(VerificationMethod.EKYC_MOCK);
        stubAccessibleVault();
        stubPendingSession(session);
        when(mockKycVerifier.isEnabled()).thenReturn(false);

        expectError(HttpStatus.BAD_REQUEST,
                () -> beneficiaryService.verifyIdentity(
                        verifyRequest(VerificationMethod.EKYC_MOCK, null, "123456789012"), currentUserId));

        verify(verificationRepository, never()).incrementAttemptCount(any());
    }

    @Test
    void initializeClaim_ekycWhenMockDisabled_throws400_andNoSessionCreated() {
        request.setVerificationMethod(VerificationMethod.EKYC_MOCK);
        stubVaultAndClaim();
        when(mockKycVerifier.isEnabled()).thenReturn(false);

        expectError(HttpStatus.BAD_REQUEST,
                () -> beneficiaryService.initializeClaim(request, currentUserId));

        verify(verificationRepository, never()).save(any());
    }

    @Test
    void sendIdentityOtp_sessionAboutToExpire_throws400() {
        IdentityVerification session = pendingSession(VerificationMethod.OTP);
        session.setCreatedAt(LocalDateTime.now().minusMinutes(12)); // còn 3 phút < 5 phút của một OTP
        stubAccessibleVault();
        stubPendingSession(session);

        expectError(HttpStatus.BAD_REQUEST,
                () -> beneficiaryService.sendIdentityOtp(request, currentUserId));

        verify(emailSender, never()).sendEmail(any(), any());
    }

    @Test
    void sendIdentityOtp_success_auditsOtpSent() {
        IdentityVerification session = pendingSession(VerificationMethod.OTP);
        stubAccessibleVault();
        stubPendingSession(session);
        when(otpGenerator.generate()).thenReturn("123456");

        beneficiaryService.sendIdentityOtp(request, currentUserId);

        verify(auditLogService).log(eq(AuditAction.IDENTITY_OTP_SENT), eq(AuditResult.SUCCESS),
                eq(currentUserId), any(), eq("Vault"), eq(vaultId.toString()), isNull());
    }

    @Test
    void verifyIdentity_wrong_auditsVerifyFailedWithAttempt() {
        IdentityVerification session = pendingSession(VerificationMethod.OTP);
        session.setOtpCode(otpHasher.hash(session.getId(), "123456"));
        session.setOtpExpiresAt(LocalDateTime.now().plusMinutes(3));
        stubAccessibleVault();
        stubPendingSession(session);
        when(verificationRepository.findAttemptCountById(session.getId())).thenReturn(1);

        expectError(HttpStatus.BAD_REQUEST,
                () -> beneficiaryService.verifyIdentity(
                        verifyRequest(VerificationMethod.OTP, "000000", null), currentUserId));

        verify(auditLogService).log(eq(AuditAction.IDENTITY_VERIFY_FAILED), eq(AuditResult.FAILURE),
                eq(currentUserId), any(), eq("Vault"), eq(vaultId.toString()), contains("attempt=1"));
    }

    @Test
    void verifyIdentity_lock_auditsIdentityLocked() {
        IdentityVerification session = pendingSession(VerificationMethod.OTP);
        session.setOtpCode(otpHasher.hash(session.getId(), "123456"));
        session.setOtpExpiresAt(LocalDateTime.now().plusMinutes(3));
        stubAccessibleVault();
        stubPendingSession(session);
        when(verificationRepository.findAttemptCountById(session.getId())).thenReturn(5);

        expectError(HttpStatus.LOCKED,
                () -> beneficiaryService.verifyIdentity(
                        verifyRequest(VerificationMethod.OTP, "000000", null), currentUserId));

        verify(auditLogService).log(eq(AuditAction.IDENTITY_LOCKED), eq(AuditResult.FAILURE),
                eq(currentUserId), any(), eq("Vault"), eq(vaultId.toString()), contains("attempts=5"));
    }

    @Test
    void getInheritedAssetDetail_success_auditsAssetViewed() throws Exception {
        UUID assetId = UUID.randomUUID();
        stubAccessibleVault();
        stubViewSession(true);
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.of(asset(assetId, "Vietcombank", AssetType.BANK_ACCOUNT)));
        when(cryptoService.decrypt(any())).thenReturn("plain");

        beneficiaryService.getInheritedAssetDetail(vaultId, assetId, currentUserId);

        verify(auditLogService).log(eq(AuditAction.ASSET_VIEWED), eq(AuditResult.SUCCESS),
                eq(currentUserId), any(), eq("DigitalAsset"), eq(assetId.toString()), isNull());
    }

    @Test
    void getInheritedAssetDetail_decryptFails_doesNotAuditAssetViewed() throws Exception {
        UUID assetId = UUID.randomUUID();
        stubAccessibleVault();
        stubViewSession(true);
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.of(asset(assetId, "Vietcombank", AssetType.BANK_ACCOUNT)));
        when(cryptoService.decrypt(any())).thenThrow(new RuntimeException("boom"));

        expectError(HttpStatus.INTERNAL_SERVER_ERROR,
                () -> beneficiaryService.getInheritedAssetDetail(vaultId, assetId, currentUserId));

        verify(auditLogService, never()).log(eq(AuditAction.ASSET_VIEWED),
                any(), any(), any(), any(), any(), any());
    }

    // ===================== FR-18: tải xuống =====================

    private void stubDownloadableAsset(UUID assetId, String name) throws Exception {
        stubAccessibleVault();
        stubViewSession(true);
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.of(asset(assetId, name, AssetType.BANK_ACCOUNT)));
        when(cryptoService.decrypt("enc-secret")).thenReturn("my-secret");
        when(cryptoService.decrypt("enc-notes")).thenReturn("my-notes");
    }

    @Test
    void downloadInheritedAsset_success_returnsDecryptedTextFile() throws Exception {
        UUID assetId = UUID.randomUUID();
        stubDownloadableAsset(assetId, "Vietcombank");

        AssetDownloadResponse file = beneficiaryService.downloadInheritedAsset(vaultId, assetId, currentUserId);

        String content = new String(file.getContent(), StandardCharsets.UTF_8);
        assertThat(file.getFileName()).isEqualTo("Vietcombank.txt");
        assertThat(content).contains("Vietcombank").contains("my-secret").contains("my-notes");
    }

    @Test
    void downloadInheritedAsset_noNotes_showsPlaceholder() throws Exception {
        UUID assetId = UUID.randomUUID();
        stubAccessibleVault();
        stubViewSession(true);
        DigitalAsset noNotes = asset(assetId, "Vietcombank", AssetType.BANK_ACCOUNT);
        noNotes.setNotesEncrypted(null);
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.of(noNotes));
        when(cryptoService.decrypt("enc-secret")).thenReturn("my-secret");

        AssetDownloadResponse file = beneficiaryService.downloadInheritedAsset(vaultId, assetId, currentUserId);

        assertThat(new String(file.getContent(), StandardCharsets.UTF_8)).contains("(không có)");
    }

    @Test
    void downloadInheritedAsset_dangerousAssetName_fileNameIsSanitized() throws Exception {
        UUID assetId = UUID.randomUUID();
        stubDownloadableAsset(assetId, "../etc/passwd\r\nX-Evil: 1");

        AssetDownloadResponse file = beneficiaryService.downloadInheritedAsset(vaultId, assetId, currentUserId);

        assertThat(file.getFileName()).doesNotContain("/", "\\", "\r", "\n", ":").endsWith(".txt");
    }

    @Test
    void downloadInheritedAsset_veryLongName_isTruncated() throws Exception {
        UUID assetId = UUID.randomUUID();
        stubDownloadableAsset(assetId, "A".repeat(200));

        AssetDownloadResponse file = beneficiaryService.downloadInheritedAsset(vaultId, assetId, currentUserId);

        assertThat(file.getFileName()).hasSize(84); // 80 ký tự + ".txt"
    }

    @Test
    void downloadInheritedAsset_blankName_usesDefaultFileName() throws Exception {
        UUID assetId = UUID.randomUUID();
        stubDownloadableAsset(assetId, "   ");

        AssetDownloadResponse file = beneficiaryService.downloadInheritedAsset(vaultId, assetId, currentUserId);

        assertThat(file.getFileName()).isEqualTo("asset.txt");
    }

    @Test
    void downloadInheritedAsset_success_auditsDownloadNotView() throws Exception {
        UUID assetId = UUID.randomUUID();
        stubDownloadableAsset(assetId, "Vietcombank");

        beneficiaryService.downloadInheritedAsset(vaultId, assetId, currentUserId);

        verify(auditLogService).log(eq(AuditAction.ASSET_DOWNLOADED), eq(AuditResult.SUCCESS),
                eq(currentUserId), any(), eq("DigitalAsset"), eq(assetId.toString()), isNull());
        verify(auditLogService, never()).log(eq(AuditAction.ASSET_VIEWED),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void downloadInheritedAsset_noViewSession_throws403AndTouchesNothing() {
        UUID assetId = UUID.randomUUID();
        stubAccessibleVault();
        stubViewSession(false);

        expectError(HttpStatus.FORBIDDEN,
                () -> beneficiaryService.downloadInheritedAsset(vaultId, assetId, currentUserId));

        verifyNoInteractions(digitalAssetRepository);
        verifyNoInteractions(cryptoService);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void downloadInheritedAsset_assetNotInVault_throws404() {
        UUID assetId = UUID.randomUUID();
        stubAccessibleVault();
        stubViewSession(true);
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.empty());

        expectError(HttpStatus.NOT_FOUND,
                () -> beneficiaryService.downloadInheritedAsset(vaultId, assetId, currentUserId));

        verifyNoInteractions(cryptoService);
    }

    @Test
    void downloadInheritedAsset_decryptFails_throws500AndDoesNotAudit() throws Exception {
        UUID assetId = UUID.randomUUID();
        stubAccessibleVault();
        stubViewSession(true);
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.of(asset(assetId, "Vietcombank", AssetType.BANK_ACCOUNT)));
        when(cryptoService.decrypt(any())).thenThrow(new RuntimeException("boom"));

        BeneficiaryException ex = expectError(HttpStatus.INTERNAL_SERVER_ERROR,
                () -> beneficiaryService.downloadInheritedAsset(vaultId, assetId, currentUserId));

        assertThat(ex.getMessage()).doesNotContain("enc-secret").doesNotContain("boom");
        verify(auditLogService, never()).log(eq(AuditAction.ASSET_DOWNLOADED),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void assetDownloadResponse_toString_doesNotContainContent() {
        AssetDownloadResponse r = AssetDownloadResponse.builder()
                .fileName("a.txt")
                .content("my-secret".getBytes(StandardCharsets.UTF_8))
                .build();
        assertThat(r.toString()).doesNotContain("content").contains("a.txt");
    }

    @Test
    void downloadInheritedAsset_locked_throws423AndNoDecrypt() {
        UUID assetId = UUID.randomUUID();
        stubVaultAndClaim();
        stubLocked(true);

        expectError(HttpStatus.LOCKED,
                () -> beneficiaryService.downloadInheritedAsset(vaultId, assetId, currentUserId));

        verifyNoInteractions(cryptoService);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void downloadInheritedAsset_vaultClaimed_throws409() {
        UUID assetId = UUID.randomUUID();
        unlockedVault.setStatus(VaultStatus.CLAIMED);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));

        expectError(HttpStatus.CONFLICT,
                () -> beneficiaryService.downloadInheritedAsset(vaultId, assetId, currentUserId));

        verifyNoInteractions(cryptoService);
        verifyNoInteractions(auditLogService);
    }

    // ===================== FR-19 =====================

    // Vault UNLOCKED + claim còn hạn + chưa bị khóa; trả về claim để test kiểm tra thay đổi
    private BeneficiaryClaim stubClaimForClose() {
        BeneficiaryClaim claim = claim(ClaimStatus.PENDING, LocalDateTime.now().plusDays(5));
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.of(claim));
        stubLocked(false);
        return claim;
    }

    private CloseVaultRequest closeRequest(Boolean confirmed) {
        return closeRequest(confirmed, null);
    }

    private CloseVaultRequest closeRequest(Boolean confirmed, Boolean acknowledgeUnviewed) {
        CloseVaultRequest r = new CloseVaultRequest();
        r.setConfirmed(confirmed);
        r.setAcknowledgeUnviewed(acknowledgeUnviewed);
        return r;
    }

    private void stubDecryptableAsset(UUID assetId) throws Exception {
        stubViewSession(true);
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.of(asset(assetId, "Vietcombank", AssetType.BANK_ACCOUNT)));
        when(cryptoService.decrypt(any())).thenReturn("plain");
    }

    @Test
    void closeVault_allViewed_closesWithoutAcknowledge() {
        BeneficiaryClaim claim = stubClaimForClose();
        stubViewSession(true);
        when(digitalAssetRepository.findUnaccessedAssets(vaultId, claim.getId())).thenReturn(List.of());
        when(claimRepository.markClaimed(eq(claim.getId()), any(LocalDateTime.class))).thenReturn(1);

        CloseVaultResponse response = beneficiaryService.closeVault(vaultId, closeRequest(true), currentUserId);

        assertThat(unlockedVault.getStatus()).isEqualTo(VaultStatus.CLAIMED);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.CLAIMED);
        assertThat(claim.getClaimedAt()).isNotNull();
        assertThat(response.getVaultStatus()).isEqualTo(VaultStatus.CLAIMED);
        assertThat(response.getClaimStatus()).isEqualTo(ClaimStatus.CLAIMED);
        assertThat(response.getClaimedAt()).isEqualTo(claim.getClaimedAt());
        verify(auditLogService).log(eq(AuditAction.CLAIM_COMPLETED), eq(AuditResult.SUCCESS),
                eq(currentUserId), any(), eq("Vault"), eq(vaultId.toString()),
                eq("unviewedCount=0, unviewedAssetIds=[]"));
    }

    @Test
    void closeVault_notConfirmed_throws400AndChangesNothing() {
        BeneficiaryClaim claim = stubClaimForClose();

        expectError(HttpStatus.BAD_REQUEST,
                () -> beneficiaryService.closeVault(vaultId, closeRequest(false), currentUserId));

        assertThat(unlockedVault.getStatus()).isEqualTo(VaultStatus.UNLOCKED);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.PENDING);
        verify(claimRepository, never()).markClaimed(any(), any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void closeVault_noViewSession_throws403AndChangesNothing() {
        stubClaimForClose();
        stubViewSession(false);

        expectError(HttpStatus.FORBIDDEN,
                () -> beneficiaryService.closeVault(vaultId, closeRequest(true), currentUserId));

        assertThat(unlockedVault.getStatus()).isEqualTo(VaultStatus.UNLOCKED);
        verify(claimRepository, never()).markClaimed(any(), any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void closeVault_unviewedWithoutAcknowledge_throws409WithCount_andChangesNothing() {
        BeneficiaryClaim claim = stubClaimForClose();
        stubViewSession(true);
        when(digitalAssetRepository.findUnaccessedAssets(vaultId, claim.getId()))
                .thenReturn(List.of(asset(UUID.randomUUID(), "Vietcombank", AssetType.BANK_ACCOUNT)));

        BeneficiaryException ex = expectError(HttpStatus.CONFLICT,
                () -> beneficiaryService.closeVault(vaultId, closeRequest(true), currentUserId));

        assertThat(ex.getMessage()).contains("Còn 1 tài sản chưa xem");
        assertThat(unlockedVault.getStatus()).isEqualTo(VaultStatus.UNLOCKED);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.PENDING);
        verify(claimRepository, never()).markClaimed(any(), any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void closeVault_unviewedAcknowledgeFalse_throws409() {
        BeneficiaryClaim claim = stubClaimForClose();
        stubViewSession(true);
        when(digitalAssetRepository.findUnaccessedAssets(vaultId, claim.getId()))
                .thenReturn(List.of(asset(UUID.randomUUID(), "Vietcombank", AssetType.BANK_ACCOUNT)));

        expectError(HttpStatus.CONFLICT,
                () -> beneficiaryService.closeVault(vaultId, closeRequest(true, false), currentUserId));

        verify(claimRepository, never()).markClaimed(any(), any());
    }

    @Test
    void closeVault_unviewedAcknowledged_closes_andAuditsUnviewedCountAndIds() {
        BeneficiaryClaim claim = stubClaimForClose();
        stubViewSession(true);
        UUID unviewedId = UUID.randomUUID();
        when(digitalAssetRepository.findUnaccessedAssets(vaultId, claim.getId()))
                .thenReturn(List.of(asset(unviewedId, "Vietcombank", AssetType.BANK_ACCOUNT)));
        when(claimRepository.markClaimed(eq(claim.getId()), any(LocalDateTime.class))).thenReturn(1);

        beneficiaryService.closeVault(vaultId, closeRequest(true, true), currentUserId);

        assertThat(unlockedVault.getStatus()).isEqualTo(VaultStatus.CLAIMED);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.CLAIMED);
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).log(eq(AuditAction.CLAIM_COMPLETED), eq(AuditResult.SUCCESS),
                eq(currentUserId), any(), eq("Vault"), eq(vaultId.toString()), detail.capture());
        assertThat(detail.getValue()).contains("unviewedCount=1").contains(unviewedId.toString());
    }

    @Test
    void closeVault_vaultWithoutAssets_closesWithoutAcknowledge() {
        BeneficiaryClaim claim = stubClaimForClose();
        stubViewSession(true);
        when(digitalAssetRepository.findUnaccessedAssets(vaultId, claim.getId())).thenReturn(List.of());
        when(claimRepository.markClaimed(eq(claim.getId()), any(LocalDateTime.class))).thenReturn(1);

        beneficiaryService.closeVault(vaultId, closeRequest(true), currentUserId);

        assertThat(unlockedVault.getStatus()).isEqualTo(VaultStatus.CLAIMED);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.CLAIMED);
    }

    @Test
    void closeVault_concurrent_secondCallGets409() {
        BeneficiaryClaim claim = stubClaimForClose();
        stubViewSession(true);
        when(digitalAssetRepository.findUnaccessedAssets(vaultId, claim.getId())).thenReturn(List.of());
        // Request song song kia đã chuyển claim sang CLAIMED trước: câu UPDATE có điều kiện không khớp dòng nào
        when(claimRepository.markClaimed(eq(claim.getId()), any(LocalDateTime.class))).thenReturn(0);

        expectError(HttpStatus.CONFLICT,
                () -> beneficiaryService.closeVault(vaultId, closeRequest(true), currentUserId));

        assertThat(unlockedVault.getStatus()).isEqualTo(VaultStatus.UNLOCKED);
        verify(vaultRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    // ----- FR-19: xem trước khi đóng hồ sơ -----

    @Test
    void previewClose_returnsTotalViewedAndUnviewedList_withoutDecrypting() {
        BeneficiaryClaim claim = stubClaimForClose();
        stubViewSession(true);
        DigitalAsset a1 = asset(UUID.randomUUID(), "Vietcombank", AssetType.BANK_ACCOUNT);
        DigitalAsset a2 = asset(UUID.randomUUID(), "Ví Binance", AssetType.CRYPTO_WALLET);
        DigitalAsset a3 = asset(UUID.randomUUID(), "Gmail", AssetType.BANK_ACCOUNT);
        when(digitalAssetRepository.findByVaultIdAndStatus(vaultId, AssetStatus.ACTIVE))
                .thenReturn(List.of(a1, a2, a3));
        when(digitalAssetRepository.findUnaccessedAssets(vaultId, claim.getId())).thenReturn(List.of(a2));

        CloseVaultPreviewResponse preview = beneficiaryService.previewClose(vaultId, currentUserId);

        assertThat(preview.getTotalAssets()).isEqualTo(3);
        assertThat(preview.getViewedCount()).isEqualTo(2);
        assertThat(preview.getUnviewedCount()).isEqualTo(1);
        assertThat(preview.getUnviewedAssets()).extracting(InheritedAssetSummaryResponse::getAssetName)
                .containsExactly("Ví Binance");
        verifyNoInteractions(cryptoService);
    }

    @Test
    void previewClose_noViewSession_throws403() {
        stubClaimForClose();
        stubViewSession(false);

        expectError(HttpStatus.FORBIDDEN,
                () -> beneficiaryService.previewClose(vaultId, currentUserId));

        verifyNoInteractions(digitalAssetRepository);
        verifyNoInteractions(cryptoService);
    }

    @Test
    void closeVault_locked_throws423() {
        stubVaultAndClaim();
        stubLocked(true);

        expectError(HttpStatus.LOCKED,
                () -> beneficiaryService.closeVault(vaultId, closeRequest(true), currentUserId));

        verifyNoInteractions(auditLogService);
    }

    @Test
    void closeVault_wrongBeneficiary_throws404() {
        User other = new User();
        other.setId(UUID.randomUUID());
        unlockedVault.setBeneficiary(other);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));

        expectError(HttpStatus.NOT_FOUND,
                () -> beneficiaryService.closeVault(vaultId, closeRequest(true), currentUserId));
    }

    @Test
    void closeVault_alreadyClaimedVault_throws409() {
        unlockedVault.setStatus(VaultStatus.CLAIMED);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));

        expectError(HttpStatus.CONFLICT,
                () -> beneficiaryService.closeVault(vaultId, closeRequest(true), currentUserId));
    }

    @Test
    void closeVault_claimPastDeadline_throws410() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.of(
                claim(ClaimStatus.PENDING, LocalDateTime.now().minusMinutes(1))));

        expectError(HttpStatus.GONE,
                () -> beneficiaryService.closeVault(vaultId, closeRequest(true), currentUserId));
    }

    // ----- Ghi nhận xem/tải xuống theo từng tài sản (FR-17, FR-18) -----

    @Test
    void getInheritedAssetDetail_firstAccess_recordsAccessForThatAsset() throws Exception {
        UUID assetId = UUID.randomUUID();
        BeneficiaryClaim claim = stubClaimForClose();
        stubDecryptableAsset(assetId);

        beneficiaryService.getInheritedAssetDetail(vaultId, assetId, currentUserId);

        verify(assetAccessRecordRepository).insertIfAbsent(
                any(UUID.class), eq(claim.getId()), eq(assetId), any(LocalDateTime.class));
    }

    @Test
    void downloadInheritedAsset_firstAccess_recordsAccess() throws Exception {
        UUID assetId = UUID.randomUUID();
        BeneficiaryClaim claim = stubClaimForClose();
        stubDecryptableAsset(assetId);

        beneficiaryService.downloadInheritedAsset(vaultId, assetId, currentUserId);

        verify(assetAccessRecordRepository).insertIfAbsent(
                any(UUID.class), eq(claim.getId()), eq(assetId), any(LocalDateTime.class));
    }

    @Test
    void getInheritedAssetDetail_secondAccessSameAsset_delegatesDedupToDatabase() throws Exception {
        UUID assetId = UUID.randomUUID();
        BeneficiaryClaim claim = stubClaimForClose();
        stubDecryptableAsset(assetId);

        beneficiaryService.getInheritedAssetDetail(vaultId, assetId, currentUserId);
        beneficiaryService.getInheritedAssetDetail(vaultId, assetId, currentUserId);

        // Service luôn gọi INSERT IGNORE cho cùng cặp (claim, asset); DB giữ đúng 1 dòng
        // (đã kiểm chứng ở test repository), nên request thứ hai không lỗi và không ghi đè thời điểm đầu.
        verify(assetAccessRecordRepository, times(2)).insertIfAbsent(
                any(UUID.class), eq(claim.getId()), eq(assetId), any(LocalDateTime.class));
    }

    @Test
    void getInheritedAssetDetail_decryptFails_doesNotRecordAccess() throws Exception {
        UUID assetId = UUID.randomUUID();
        stubClaimForClose();
        stubViewSession(true);
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.of(asset(assetId, "Vietcombank", AssetType.BANK_ACCOUNT)));
        when(cryptoService.decrypt(any())).thenThrow(new RuntimeException("boom"));

        expectError(HttpStatus.INTERNAL_SERVER_ERROR,
                () -> beneficiaryService.getInheritedAssetDetail(vaultId, assetId, currentUserId));

        verifyNoInteractions(assetAccessRecordRepository);
    }

    @Test
    void getInheritedAssets_list_doesNotRecordAccess() {
        stubClaimForClose();
        stubViewSession(true);
        when(digitalAssetRepository.findByVaultIdAndStatus(vaultId, AssetStatus.ACTIVE))
                .thenReturn(List.of());

        beneficiaryService.getInheritedAssets(vaultId, currentUserId);

        verifyNoInteractions(assetAccessRecordRepository);
    }

}