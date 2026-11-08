package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.service.authservice.GoogleTokenVerifier;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import com.ltld.app.legacyvault.utility.OtpHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailAssetVerifier – C.3.1 xác thực tài sản EMAIL")
class EmailAssetVerifierTest {

    @Mock private OtpGenerator otpGenerator;
    @Mock private EmailSender emailSender;
    @Mock private GoogleTokenVerifier googleTokenVerifier;

    private OtpHasher otpHasher;
    private EmailAssetVerifier verifier;
    private UUID ownerId;
    private UUID assetId;

    @BeforeEach
    void setUp() {
        otpHasher = new OtpHasher();
        verifier = new EmailAssetVerifier(otpGenerator, otpHasher, emailSender, googleTokenVerifier);
        ownerId = UUID.randomUUID();
        assetId = UUID.randomUUID();
    }

    private DigitalAsset emailAsset(String platform, String email) {
        return DigitalAsset.builder()
                .id(assetId)
                .assetType(AssetType.EMAIL)
                .assetName("Primary Email")
                .publicFieldsJson("{\"platform\":\"" + platform + "\",\"usernameOrHandle\":\"" + email + "\"}")
                .build();
    }

    @Test
    @DisplayName("OTP_EMAIL: start gửi mã OTP tới đúng địa chỉ và confirm khớp mã trả REAL")
    void otpEmail_startAndConfirm_success() {
        DigitalAsset asset = emailAsset("Gmail", "owner.vault@gmail.com");
        when(otpGenerator.generate()).thenReturn("482910");

        AssetVerifier.StartResult start = verifier.start(asset, AssetVerificationMethod.OTP_EMAIL, ownerId);

        assertThat(start.provider()).isEqualTo(VerificationProvider.REAL);
        assertThat(start.challengeHash()).hasSize(64);
        assertThat(start.clientData()).containsEntry("email", "owner.vault@gmail.com");
        verify(emailSender).sendEmail("owner.vault@gmail.com", "482910");

        AssetVerifier.VerifyResult confirm = verifier.confirm(
                asset, AssetVerificationMethod.OTP_EMAIL, Map.of("code", "482910"), ownerId, start.challengeHash());

        assertThat(confirm.success()).isTrue();
        assertThat(confirm.provider()).isEqualTo(VerificationProvider.REAL);
        assertThat(confirm.evidence()).containsEntry("verifiedEmail", "owner.vault@gmail.com");
    }

    @Test
    @DisplayName("OTP_EMAIL: nhập sai mã OTP trả success=false")
    void otpEmail_wrongCode_returnsFailure() {
        DigitalAsset asset = emailAsset("Outlook", "user@outlook.com");
        when(otpGenerator.generate()).thenReturn("123456");

        AssetVerifier.StartResult start = verifier.start(asset, AssetVerificationMethod.OTP_EMAIL, ownerId);
        AssetVerifier.VerifyResult confirm = verifier.confirm(
                asset, AssetVerificationMethod.OTP_EMAIL, Map.of("code", "999999"), ownerId, start.challengeHash());

        assertThat(confirm.success()).isFalse();
        assertThat(confirm.provider()).isEqualTo(VerificationProvider.REAL);
    }

    @Test
    @DisplayName("GOOGLE_LOGIN: xác thực ID token hợp lệ khớp địa chỉ Gmail trả REAL")
    void googleLogin_matchingEmail_success() {
        DigitalAsset asset = emailAsset("Gmail", "Owner.Vault@gmail.com");
        when(googleTokenVerifier.verify("valid-id-token")).thenReturn(
                new GoogleTokenVerifier.GoogleProfile("google-sub-123", "owner.vault@gmail.com", "Owner"));

        AssetVerifier.VerifyResult result = verifier.confirm(
                asset, AssetVerificationMethod.GOOGLE_LOGIN, Map.of("idToken", "valid-id-token"), ownerId, null);

        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo(VerificationProvider.REAL);
        assertThat(result.evidence())
                .containsEntry("verifiedEmail", "owner.vault@gmail.com")
                .containsEntry("googleSubject", "google-sub-123");
    }

    @Test
    @DisplayName("GOOGLE_LOGIN: đăng nhập nhầm tài khoản Google khác với email đã khai trả success=false")
    void googleLogin_mismatchedEmail_fails() {
        DigitalAsset asset = emailAsset("Gmail", "owner.vault@gmail.com");
        when(googleTokenVerifier.verify("other-id-token")).thenReturn(
                new GoogleTokenVerifier.GoogleProfile("sub-999", "someone.else@gmail.com", "Other"));

        AssetVerifier.VerifyResult result = verifier.confirm(
                asset, AssetVerificationMethod.GOOGLE_LOGIN, Map.of("idToken", "other-id-token"), ownerId, null);

        assertThat(result.success()).isFalse();
    }

    @Test
    @DisplayName("GOOGLE_LOGIN: token Google không hợp lệ trả success=false")
    void googleLogin_invalidToken_returnsFailure() {
        DigitalAsset asset = emailAsset("Gmail", "owner.vault@gmail.com");
        when(googleTokenVerifier.verify("bad-token"))
                .thenThrow(new BusinessException(ErrorCode.GOOGLE_TOKEN_INVALID, "Invalid Google credential"));

        AssetVerifier.VerifyResult result = verifier.confirm(
                asset, AssetVerificationMethod.GOOGLE_LOGIN, Map.of("idToken", "bad-token"), ownerId, null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Invalid Google credential");
    }

    @Test
    @DisplayName("GOOGLE_LOGIN: tài khoản Outlook không phải Gmail bị chặn với 400")
    void googleLogin_nonGmailAccount_throws400() {
        DigitalAsset asset = emailAsset("Outlook", "owner@outlook.com");

        assertThatThrownBy(() -> verifier.start(asset, AssetVerificationMethod.GOOGLE_LOGIN, ownerId))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
