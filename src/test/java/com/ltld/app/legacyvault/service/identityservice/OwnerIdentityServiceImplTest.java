package com.ltld.app.legacyvault.service.identityservice;

import com.ltld.app.legacyvault.dto.identitydto.IdentityExtractResponse;
import com.ltld.app.legacyvault.dto.identitydto.IdentityStatusResponse;
import com.ltld.app.legacyvault.dto.identitydto.PhoneOtpResponse;
import com.ltld.app.legacyvault.dto.identitydto.SubmitIdentityRequest;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.OwnerIdentity;
import com.ltld.app.legacyvault.entity.PhoneOtpChallenge;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.*;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.UserProfileException;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.OwnerIdentityRepository;
import com.ltld.app.legacyvault.repository.PhoneOtpChallengeRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.UploadedDocumentValidator;
import com.ltld.app.legacyvault.utility.CitizenId;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import com.ltld.app.legacyvault.utility.OtpHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OwnerIdentityServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-11T03:00:00Z");
    private static final String CCCD = "079201123456";   // TP.HCM, nam, sinh 2001

    @Mock private UserRepository userRepository;
    @Mock private OwnerIdentityRepository identityRepository;
    @Mock private PhoneOtpChallengeRepository otpRepository;
    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private CryptoService cryptoService;
    @Mock private OtpGenerator otpGenerator;
    @Mock private AuditLogService auditLogService;
    @Mock private UploadedDocumentValidator uploadedDocumentValidator;
    @Mock private CitizenIdOcrClient ocrClient;

    private final OtpHasher otpHasher = new OtpHasher();
    private OwnerIdentityServiceImpl service;
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        service = new OwnerIdentityServiceImpl(userRepository, identityRepository, otpRepository, dmsConfigRepository,
                cryptoService, otpGenerator, otpHasher, new com.ltld.app.legacyvault.utility.IdentityHasher(),
                auditLogService, uploadedDocumentValidator, ocrClient,
                Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "smsSandbox", true);
        userId = UUID.randomUUID();
        user = User.builder().id(userId).email("owner@example.test").fullName("Nguyễn Văn An")
                .phoneNumber("+84912345678").phoneVerifiedAt(NOW.minusSeconds(60)).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(identityRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(identityRepository.findByCitizenIdHash(anyString())).thenReturn(Optional.empty());
        when(dmsConfigRepository.findByOwnerId(userId)).thenReturn(Optional.empty());
        when(cryptoService.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        when(cryptoService.decrypt(anyString())).thenAnswer(inv -> ((String) inv.getArgument(0)).substring(4));
        when(otpGenerator.generate()).thenReturn("123456");
    }

    private static SubmitIdentityRequest request() {
        SubmitIdentityRequest r = new SubmitIdentityRequest();
        r.setCitizenId(CCCD);
        r.setOldIdNumber("025123456");
        r.setFullName("NGUYEN  VAN AN");
        r.setDateOfBirth(LocalDate.of(2001, 5, 2));
        r.setGender(CitizenId.Gender.MALE);
        r.setResidenceProvince(VnProvince.HO_CHI_MINH);
        r.setSource(KycSource.QR);
        r.setDataConsent(true);
        return r;
    }

    private void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(com.ltld.app.legacyvault.exception.BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    // ------------------------------------------------------------ submit

    @Test
    void submit_valid_savesEncryptedIdentity_andSetsKycBasic() {
        IdentityStatusResponse res = service.submit(userId, request());

        ArgumentCaptor<OwnerIdentity> captor = ArgumentCaptor.forClass(OwnerIdentity.class);
        verify(identityRepository).save(captor.capture());
        OwnerIdentity saved = captor.getValue();
        assertThat(saved.getCitizenIdEncrypted()).isEqualTo("enc:" + CCCD);
        assertThat(saved.getCitizenIdHash()).isNotBlank().doesNotContain(CCCD);
        assertThat(saved.getCitizenIdMasked()).isEqualTo("0792••••3456");
        assertThat(saved.getOldIdEncrypted()).isEqualTo("enc:025123456");
        assertThat(saved.getFullName()).isEqualTo("NGUYEN VAN AN");
        assertThat(saved.getSource()).isEqualTo(KycSource.QR);
        assertThat(user.getKycLevel()).isEqualTo(KycLevel.BASIC);
        // Họ tên khớp khi bỏ dấu: không đổi họ tên tài khoản
        assertThat(user.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(res.isVerified()).isTrue();
        assertThat(res.getProvinces()).hasSize(34);
        verify(auditLogService).log(eq(AuditAction.OWNER_IDENTITY_VERIFIED), eq(AuditResult.SUCCESS), eq(userId),
                anyString(), isNull(), eq("User"), eq(userId.toString()), eq("source=QR"));
    }

    @Test
    void submit_withoutDataConsent_isRejected() {
        SubmitIdentityRequest r = request();
        r.setDataConsent(false);
        assertCode(() -> service.submit(userId, r), ErrorCode.KYC_CONSENT_REQUIRED);
    }

    @Test
    void submit_ocrSourceWithoutAiConsent_isRejected() {
        SubmitIdentityRequest r = request();
        r.setSource(KycSource.OCR);
        assertCode(() -> service.submit(userId, r), ErrorCode.KYC_CONSENT_REQUIRED);
    }

    @Test
    void submit_phoneNotVerified_isRejected() {
        user.setPhoneVerifiedAt(null);
        assertCode(() -> service.submit(userId, request()), ErrorCode.PHONE_NOT_VERIFIED);
    }

    @Test
    void submit_birthYearNotMatchingCitizenId_isRejected() {
        SubmitIdentityRequest r = request();
        r.setDateOfBirth(LocalDate.of(1990, 5, 2));
        assertCode(() -> service.submit(userId, r), ErrorCode.CITIZEN_ID_INVALID);
    }

    @Test
    void submit_genderNotMatchingCitizenId_isRejected() {
        SubmitIdentityRequest r = request();
        r.setGender(CitizenId.Gender.FEMALE);
        assertCode(() -> service.submit(userId, r), ErrorCode.CITIZEN_ID_INVALID);
    }

    @Test
    void submit_invalidProvinceCode_isRejected() {
        SubmitIdentityRequest r = request();
        r.setCitizenId("003201123456");
        assertCode(() -> service.submit(userId, r), ErrorCode.CITIZEN_ID_INVALID);
    }

    @Test
    void submit_under18_isRejected() {
        SubmitIdentityRequest r = request();
        r.setCitizenId("079209123456");   // sinh 2009
        r.setDateOfBirth(LocalDate.of(2009, 12, 1));
        assertCode(() -> service.submit(userId, r), ErrorCode.IDENTITY_UNDERAGE);
    }

    @Test
    void submit_citizenIdOnAnotherAccount_isRejected() {
        User other = User.builder().id(UUID.randomUUID()).build();
        when(identityRepository.findByCitizenIdHash(anyString()))
                .thenReturn(Optional.of(OwnerIdentity.builder().user(other).build()));
        assertCode(() -> service.submit(userId, request()), ErrorCode.CITIZEN_ID_TAKEN);
        verify(identityRepository, never()).save(any());
    }

    @Test
    void submit_nameMismatch_requiresConfirmation_thenUpdatesAccountName() {
        user.setFullName("Tên Khác Hẳn");
        assertCode(() -> service.submit(userId, request()), ErrorCode.IDENTITY_NAME_MISMATCH);

        SubmitIdentityRequest r = request();
        r.setUpdateAccountName(true);
        service.submit(userId, r);
        assertThat(user.getFullName()).isEqualTo("NGUYEN VAN AN");
    }

    @Test
    void submit_update_requiresRecentPhoneReverification() {
        OwnerIdentity existing = OwnerIdentity.builder().user(user).build();
        when(identityRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        user.setKycLevel(KycLevel.BASIC);
        user.setPhoneVerifiedAt(NOW.minus(Duration.ofHours(2)));

        assertCode(() -> service.submit(userId, request()), ErrorCode.PHONE_NOT_VERIFIED);

        user.setPhoneVerifiedAt(NOW.minus(Duration.ofMinutes(5)));
        service.submit(userId, request());
        verify(auditLogService).log(eq(AuditAction.OWNER_IDENTITY_UPDATED), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void submit_whileDmsTriggered_isLocked() {
        when(dmsConfigRepository.findByOwnerId(userId))
                .thenReturn(Optional.of(DmsConfig.builder().status(DmsStatus.TRIGGERED).build()));
        assertCode(() -> service.submit(userId, request()), ErrorCode.IDENTITY_LOCKED);
    }

    @Test
    void submit_oldIdSameAsCitizenId_isIgnored_andBadOldIdRejected() {
        SubmitIdentityRequest r = request();
        r.setOldIdNumber(CCCD);
        service.submit(userId, r);
        ArgumentCaptor<OwnerIdentity> captor = ArgumentCaptor.forClass(OwnerIdentity.class);
        verify(identityRepository).save(captor.capture());
        assertThat(captor.getValue().getOldIdEncrypted()).isNull();

        SubmitIdentityRequest bad = request();
        bad.setOldIdNumber("1234");
        assertCode(() -> service.submit(userId, bad), ErrorCode.CITIZEN_ID_INVALID);
    }

    // ------------------------------------------------------------ phone OTP

    @Test
    void sendPhoneOtp_storesHashedCode_andReturnsSandboxCode() {
        when(otpRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        PhoneOtpResponse res = service.sendPhoneOtp(userId, "0386 123 456");

        assertThat(res.getMaskedPhone()).isEqualTo("0386 ••• 456");
        assertThat(res.getCarrier()).isEqualTo("Viettel");
        assertThat(res.getSandboxCode()).isEqualTo("123456");
        ArgumentCaptor<PhoneOtpChallenge> captor = ArgumentCaptor.forClass(PhoneOtpChallenge.class);
        verify(otpRepository).save(captor.capture());
        assertThat(captor.getValue().getPhoneE164()).isEqualTo("+84386123456");
        assertThat(captor.getValue().getCodeHash()).doesNotContain("123456");
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void sendPhoneOtp_smsNotConfigured_failsBeforeCreatingCode() {
        ReflectionTestUtils.setField(service, "smsSandbox", false);

        assertCode(() -> service.sendPhoneOtp(userId, "0912345678"), ErrorCode.SERVICE_UNAVAILABLE);
        verifyNoInteractions(otpRepository, otpGenerator);
    }

    @Test
    void verifyPhoneOtp_auditRecordsSandboxMode() {
        PhoneOtpChallenge c = challenge("+84386123456", "654321");
        when(otpRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(c));

        service.verifyPhoneOtp(userId, "654321");

        verify(auditLogService).log(eq(AuditAction.OWNER_PHONE_VERIFIED), eq(AuditResult.SUCCESS), eq(userId),
                anyString(), isNull(), eq("User"), eq(userId.toString()), eq("sandbox=true"));
    }

    @Test
    void sendPhoneOtp_invalidPhone_isRejected() {
        assertCode(() -> service.sendPhoneOtp(userId, "01681234567"), ErrorCode.PHONE_INVALID);
    }

    @Test
    void sendPhoneOtp_withinCooldown_isRejected() {
        when(otpRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(PhoneOtpChallenge.builder()
                .userId(userId).sentAt(NOW.minusSeconds(10)).windowStartedAt(NOW.minusSeconds(10))
                .windowSendCount(1).build()));
        assertCode(() -> service.sendPhoneOtp(userId, "0912345678"), ErrorCode.TOO_MANY_OTP_REQUESTS);
    }

    @Test
    void sendPhoneOtp_tooManyInOneHour_isRejected() {
        when(otpRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(PhoneOtpChallenge.builder()
                .userId(userId).sentAt(NOW.minusSeconds(120)).windowStartedAt(NOW.minus(Duration.ofMinutes(30)))
                .windowSendCount(5).build()));
        assertCode(() -> service.sendPhoneOtp(userId, "0912345678"), ErrorCode.TOO_MANY_OTP_REQUESTS);
    }

    private PhoneOtpChallenge challenge(String phone, String code) {
        return PhoneOtpChallenge.builder().userId(userId).phoneE164(phone)
                .codeHash(otpHasher.hash(userId, phone + ":" + code))
                .sentAt(NOW.minusSeconds(30)).expiresAt(NOW.plusSeconds(270))
                .windowStartedAt(NOW.minusSeconds(30)).windowSendCount(1).build();
    }

    @Test
    void verifyPhoneOtp_correctCode_savesPhoneAndVerificationTime() {
        user.setPhoneVerifiedAt(null);
        PhoneOtpChallenge c = challenge("+84386123456", "654321");
        when(otpRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(c));

        IdentityStatusResponse res = service.verifyPhoneOtp(userId, "654321");

        assertThat(user.getPhoneNumber()).isEqualTo("+84386123456");
        assertThat(user.getPhoneVerifiedAt()).isEqualTo(NOW);
        assertThat(res.getPhoneNumber()).isEqualTo("0386 123 456");
        assertThat(res.isPhoneVerified()).isTrue();
        verify(otpRepository).delete(c);
    }

    @Test
    void verifyPhoneOtp_wrongCode_countsAttempt() {
        PhoneOtpChallenge c = challenge("+84386123456", "654321");
        when(otpRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(c));

        assertCode(() -> service.verifyPhoneOtp(userId, "000000"), ErrorCode.OTP_INVALID);
        assertThat(c.getAttempts()).isEqualTo(1);
        assertThat(user.getPhoneNumber()).isEqualTo("+84912345678");
    }

    @Test
    void verifyPhoneOtp_expiredOrTooManyAttempts_isRejected() {
        PhoneOtpChallenge expired = challenge("+84386123456", "654321");
        expired.setExpiresAt(NOW.minusSeconds(1));
        when(otpRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(expired));
        assertCode(() -> service.verifyPhoneOtp(userId, "654321"), ErrorCode.OTP_EXPIRED);

        PhoneOtpChallenge exhausted = challenge("+84386123456", "654321");
        exhausted.setAttempts(5);
        when(otpRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(exhausted));
        assertCode(() -> service.verifyPhoneOtp(userId, "654321"), ErrorCode.TOO_MANY_ATTEMPTS);
    }

    // ------------------------------------------------------------ extract

    @Test
    void extractFromQr_returnsFieldsAndNewProvince() {
        IdentityExtractResponse res = service.extractFromQr(userId,
                "079201123456|025123456|NGUYỄN VĂN AN|02052001|Nam|Thị xã Thuận An, Tỉnh Bình Dương|15072021");

        assertThat(res.getSource()).isEqualTo("QR");
        assertThat(res.getCitizenId()).isEqualTo(CCCD);
        assertThat(res.getOldIdNumber()).isEqualTo("025123456");
        assertThat(res.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(res.getGender()).isEqualTo("MALE");
        assertThat(res.getResidenceProvince()).isEqualTo("HO_CHI_MINH");
        // Đọc được tỉnh từ địa chỉ thì không gợi ý theo số CCCD
        assertThat(res.getSuggestedProvince()).isNull();
        assertThat(res.getWarnings()).isEmpty();
    }

    @Test
    void extractFromQr_addressWithoutProvince_suggestsFromCitizenIdPrefix() {
        IdentityExtractResponse res = service.extractFromQr(userId,
                "079201123456|025123456|NGUYỄN VĂN AN|02052001|Nam|Số 1 Đường ABC|15072021");

        assertThat(res.getResidenceProvince()).isNull();   // không chọn sẵn
        assertThat(res.getSuggestedProvince()).isEqualTo("HO_CHI_MINH");
        assertThat(res.getWarnings()).containsExactly("PROVINCE_SUGGESTED_FROM_ID");
    }

    @Test
    void extractFromQr_invalidContent_isRejected() {
        assertCode(() -> service.extractFromQr(userId, "https://example.com"), ErrorCode.CITIZEN_QR_INVALID);
    }

    @Test
    void extractFromImage_withoutAiConsent_isRejected_andImageNotRead() {
        assertCode(() -> service.extractFromImage(userId, null, false), ErrorCode.KYC_CONSENT_REQUIRED);
        verifyNoInteractions(ocrClient, uploadedDocumentValidator);
    }

    @Test
    void extractFromImage_mapsAiFields_andAlwaysAsksForReview() {
        when(uploadedDocumentValidator.readAndValidate(any())).thenReturn(new byte[]{1});
        when(ocrClient.read(any())).thenReturn(new CitizenIdOcrClient.Extracted(
                "079 201 123 456", "NGUYỄN VĂN AN", "02/05/2001", "Nam", "Quận 1, TP. Hồ Chí Minh", "CCCD"));

        IdentityExtractResponse res = service.extractFromImage(userId, null, true);

        assertThat(res.getCitizenId()).isEqualTo(CCCD);
        assertThat(res.getDateOfBirth()).isEqualTo(LocalDate.of(2001, 5, 2));
        assertThat(res.getResidenceProvince()).isEqualTo("HO_CHI_MINH");
        assertThat(res.getSuggestedProvince()).isNull();
        assertThat(res.getWarnings()).containsExactly("REVIEW_AI_RESULT");
    }

    @Test
    void extractFromImage_addressUnreadable_suggestsFromCitizenIdPrefix() {
        when(uploadedDocumentValidator.readAndValidate(any())).thenReturn(new byte[]{1});
        when(ocrClient.read(any())).thenReturn(new CitizenIdOcrClient.Extracted(
                "082 195 654 321", "TRẦN THỊ BÌNH", "10/03/1995", "Nữ", null, "CCCD"));

        IdentityExtractResponse res = service.extractFromImage(userId, null, true);

        assertThat(res.getResidenceProvince()).isNull();
        assertThat(res.getSuggestedProvince()).isEqualTo("DONG_THAP");   // 082 Tiền Giang -> Đồng Tháp
        assertThat(res.getWarnings()).containsExactly("PROVINCE_SUGGESTED_FROM_ID", "REVIEW_AI_RESULT");
    }

    @Test
    void extractFromImage_noAddressAndNoReadableId_noSuggestion() {
        when(uploadedDocumentValidator.readAndValidate(any())).thenReturn(new byte[]{1});
        when(ocrClient.read(any())).thenReturn(new CitizenIdOcrClient.Extracted(
                "0821956", "TRẦN THỊ BÌNH", "10/03/1995", "Nữ", null, "CCCD"));

        IdentityExtractResponse res = service.extractFromImage(userId, null, true);

        assertThat(res.getSuggestedProvince()).isNull();
        assertThat(res.getWarnings()).containsExactly("CITIZEN_ID_UNREADABLE", "PROVINCE_NOT_DETECTED", "REVIEW_AI_RESULT");
    }

    // ------------------------------------------------------------ death certificate match

    @Test
    void matchDocumentId_doesNotDependOnHashKey() {
        // Hash lưu bằng một khoá khác (vd. khoá đã bị đổi): đối chiếu vẫn đúng vì so bản giải mã
        OwnerIdentity identity = OwnerIdentity.builder().user(user)
                .citizenIdEncrypted("enc:" + CCCD).citizenIdHash("hash-from-an-old-key").build();
        when(identityRepository.findByUserId(userId)).thenReturn(Optional.of(identity));

        assertThat(service.matchDocumentId(userId, CCCD)).isEqualTo(OwnerIdMatch.MATCHED);
        assertThat(service.matchDocumentId(userId, "025123456")).isEqualTo(OwnerIdMatch.UNVERIFIABLE);
    }

    @Test
    void matchDocumentId_comparesCitizenIdAndOldId() {
        service.submit(userId, request());
        ArgumentCaptor<OwnerIdentity> captor = ArgumentCaptor.forClass(OwnerIdentity.class);
        verify(identityRepository).save(captor.capture());
        when(identityRepository.findByUserId(userId)).thenReturn(Optional.of(captor.getValue()));

        assertThat(service.matchDocumentId(userId, "079 201 123 456")).isEqualTo(OwnerIdMatch.MATCHED);
        assertThat(service.matchDocumentId(userId, "025123456")).isEqualTo(OwnerIdMatch.MATCHED);
        assertThat(service.matchDocumentId(userId, "079201999999")).isEqualTo(OwnerIdMatch.MISMATCHED);
        assertThat(service.matchDocumentId(userId, "025999999")).isEqualTo(OwnerIdMatch.MISMATCHED);
        assertThat(service.matchDocumentId(userId, null)).isEqualTo(OwnerIdMatch.NOT_ON_DOCUMENT);
        assertThat(service.matchDocumentId(userId, "12345")).isEqualTo(OwnerIdMatch.UNVERIFIABLE);
        assertThat(service.matchDocumentId(UUID.randomUUID(), CCCD)).isEqualTo(OwnerIdMatch.OWNER_NOT_VERIFIED);
    }
}
