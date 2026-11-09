package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import com.ltld.app.legacyvault.utility.OtpHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeAssetVerifier – C.3.4 xác thực tài sản EXCHANGE_ACCOUNT (mô phỏng)")
class ExchangeAssetVerifierTest {

    @Mock private OtpGenerator otpGenerator;
    @Mock private EmailSender emailSender;

    private ExchangeAssetVerifier verifier;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        verifier = new ExchangeAssetVerifier(otpGenerator, new OtpHasher(), emailSender);
        ownerId = UUID.randomUUID();
    }

    private static DigitalAsset exchangeAsset(String accountId, String registeredEmail) {
        StringBuilder json = new StringBuilder("{\"institutionName\":\"Binance\",\"accountId\":\"" + accountId + "\"");
        if (registeredEmail != null) {
            json.append(",\"registeredEmail\":\"").append(registeredEmail).append("\"");
        }
        json.append("}");
        return DigitalAsset.builder()
                .id(UUID.randomUUID())
                .assetType(AssetType.EXCHANGE_ACCOUNT)
                .assetName("Binance")
                .publicFieldsJson(json.toString())
                .build();
    }

    @Test
    @DisplayName("type() là EXCHANGE_ACCOUNT, methods() chỉ có EXCHANGE_EMAIL_CODE")
    void typeAndMethods() {
        assertThat(verifier.type()).isEqualTo(AssetType.EXCHANGE_ACCOUNT);
        assertThat(verifier.methods()).containsExactly(AssetVerificationMethod.EXCHANGE_EMAIL_CODE);
    }

    @Test
    @DisplayName("start: gửi mã tới registeredEmail, trả SANDBOX, challengeHash 64 ký tự, email được che")
    void start_usesRegisteredEmail() {
        when(otpGenerator.generate()).thenReturn("482910");

        AssetVerifier.StartResult start = verifier.start(exchangeAsset("u123", "owner@binance.com"),
                AssetVerificationMethod.EXCHANGE_EMAIL_CODE, ownerId);

        assertThat(start.provider()).isEqualTo(VerificationProvider.SANDBOX);
        assertThat(start.challengeHash()).hasSize(64);
        assertThat(start.challengeExpiresAt()).isNotNull();
        assertThat(start.clientData()).containsEntry("maskedEmail", "o***r@binance.com");
        verify(emailSender).sendAssetVerificationOtpEmail("owner@binance.com", "482910", 5);
    }

    @Test
    @DisplayName("start: không có registeredEmail thì dùng accountId nếu accountId có dạng email")
    void start_fallsBackToAccountIdEmail() {
        when(otpGenerator.generate()).thenReturn("111111");

        verifier.start(exchangeAsset("trader@mail.com", null),
                AssetVerificationMethod.EXCHANGE_EMAIL_CODE, ownerId);

        verify(emailSender).sendAssetVerificationOtpEmail("trader@mail.com", "111111", 5);
    }

    @Test
    @DisplayName("start: registeredEmail được ưu tiên hơn accountId dạng email")
    void start_prefersRegisteredEmail() {
        when(otpGenerator.generate()).thenReturn("222222");

        verifier.start(exchangeAsset("other@mail.com", "owner@binance.com"),
                AssetVerificationMethod.EXCHANGE_EMAIL_CODE, ownerId);

        verify(emailSender).sendAssetVerificationOtpEmail("owner@binance.com", "222222", 5);
    }

    @Test
    @DisplayName("start: không có email hợp lệ (accountId không phải email) thì từ chối, không gửi mail")
    void start_withoutEmail_throws() {
        assertThatThrownBy(() -> verifier.start(exchangeAsset("user123", null),
                AssetVerificationMethod.EXCHANGE_EMAIL_CODE, ownerId)).isInstanceOf(VaultException.class);
        verifyNoInteractions(emailSender, otpGenerator);
    }

    @Test
    @DisplayName("start: registeredEmail sai định dạng thì từ chối")
    void start_invalidRegisteredEmail_throws() {
        assertThatThrownBy(() -> verifier.start(exchangeAsset("u123", "not-an-email"),
                AssetVerificationMethod.EXCHANGE_EMAIL_CODE, ownerId)).isInstanceOf(VaultException.class);
        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("confirm: đúng mã thì thành công (SANDBOX), evidence chỉ có email đã che")
    void confirm_correctCode_success() {
        when(otpGenerator.generate()).thenReturn("482910");
        DigitalAsset asset = exchangeAsset("u123", "owner@binance.com");
        AssetVerifier.StartResult start = verifier.start(asset, AssetVerificationMethod.EXCHANGE_EMAIL_CODE, ownerId);

        AssetVerifier.VerifyResult result = verifier.confirm(asset, AssetVerificationMethod.EXCHANGE_EMAIL_CODE,
                Map.of("code", "482910"), ownerId, start.challengeHash());

        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo(VerificationProvider.SANDBOX);
        assertThat(result.evidence())
                .containsEntry("check", "EMAIL_CODE")
                .containsEntry("maskedEmail", "o***r@binance.com");
        assertThat(result.evidence().values()).noneMatch(v -> v.contains("owner@binance.com"));
    }

    @Test
    @DisplayName("confirm: sai mã thì thất bại")
    void confirm_wrongCode_fails() {
        when(otpGenerator.generate()).thenReturn("482910");
        DigitalAsset asset = exchangeAsset("u123", "owner@binance.com");
        AssetVerifier.StartResult start = verifier.start(asset, AssetVerificationMethod.EXCHANGE_EMAIL_CODE, ownerId);

        AssetVerifier.VerifyResult result = verifier.confirm(asset, AssetVerificationMethod.EXCHANGE_EMAIL_CODE,
                Map.of("code", "999999"), ownerId, start.challengeHash());

        assertThat(result.success()).isFalse();
    }

    @Test
    @DisplayName("confirm: thiếu mã hoặc chưa start (không có challengeHash) thì thất bại")
    void confirm_missingCodeOrChallenge_fails() {
        DigitalAsset asset = exchangeAsset("u123", "owner@binance.com");

        assertThat(verifier.confirm(asset, AssetVerificationMethod.EXCHANGE_EMAIL_CODE,
                Map.of(), ownerId, "a".repeat(64)).success()).isFalse();
        assertThat(verifier.confirm(asset, AssetVerificationMethod.EXCHANGE_EMAIL_CODE,
                Map.of("code", "482910"), ownerId, null).success()).isFalse();
    }

    @Test
    @DisplayName("Cách không thuộc EXCHANGE_ACCOUNT (OTP_EMAIL) bị từ chối ở start và confirm")
    void unsupportedMethod_throws() {
        DigitalAsset asset = exchangeAsset("u123", "owner@binance.com");

        assertThatThrownBy(() -> verifier.start(asset, AssetVerificationMethod.OTP_EMAIL, ownerId))
                .isInstanceOf(VaultException.class);
        assertThatThrownBy(() -> verifier.confirm(asset, AssetVerificationMethod.OTP_EMAIL,
                Map.of(), ownerId, null)).isInstanceOf(VaultException.class);
    }
}
