package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.exception.VaultException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EWalletAssetVerifier – C.3.2 xác thực tài sản E_WALLET")
class EWalletAssetVerifierTest {

    @Mock private OtpGenerator otpGenerator;

    private EWalletAssetVerifier verifier;
    private UUID ownerId;
    private UUID assetId;

    @BeforeEach
    void setUp() {
        verifier = new EWalletAssetVerifier(otpGenerator, new OtpHasher(), "paypal-sandbox-client-id");
        ownerId = UUID.randomUUID();
        assetId = UUID.randomUUID();
    }

    private DigitalAsset eWalletAsset(String platform, String handle) {
        return DigitalAsset.builder()
                .id(assetId)
                .assetType(AssetType.E_WALLET)
                .assetName(platform + " Wallet")
                .publicFieldsJson("{\"platform\":\"" + platform + "\",\"usernameOrHandle\":\"" + handle + "\"}")
                .build();
    }

    @Test
    @DisplayName("SMS_CODE_MOCK: kiểm định dạng SĐT Việt Nam, nhận diện nhà mạng Viettel và xác nhận mã SMS mô phỏng")
    void smsCodeMock_validViettelPhone_success() {
        DigitalAsset asset = eWalletAsset("MoMo", "+84 987 654 321");
        when(otpGenerator.generate()).thenReturn("314159");

        AssetVerifier.StartResult start = verifier.start(asset, AssetVerificationMethod.SMS_CODE_MOCK, ownerId);

        assertThat(start.provider()).isEqualTo(VerificationProvider.SANDBOX);
        assertThat(start.clientData())
                .containsEntry("carrier", "Viettel")
                .containsEntry("sandboxCode", "314159")
                .containsEntry("mode", "SANDBOX");

        AssetVerifier.VerifyResult confirm = verifier.confirm(
                asset, AssetVerificationMethod.SMS_CODE_MOCK, Map.of("code", "314159"), ownerId, start.challengeHash());

        assertThat(confirm.success()).isTrue();
        assertThat(confirm.provider()).isEqualTo(VerificationProvider.SANDBOX);
        assertThat(confirm.evidence())
                .containsEntry("carrier", "Viettel")
                .containsEntry("platform", "MoMo");
    }

    @Test
    @DisplayName("SMS_CODE_MOCK: số điện thoại không đúng chuẩn di động Việt Nam bị chặn 400")
    void smsCodeMock_invalidPhone_throws400() {
        DigitalAsset asset = eWalletAsset("ZaloPay", "012345");

        assertThatThrownBy(() -> verifier.start(asset, AssetVerificationMethod.SMS_CODE_MOCK, ownerId))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("PAYPAL_LOGIN: xác thực qua PayPal OAuth accessToken khớp email khai báo")
    void paypalLogin_oauthAccessTokenMatchesEmail_success() {
        DigitalAsset asset = eWalletAsset("PayPal", "buyer.sandbox@paypal.com");
        verifier.setPayPalEmailLookup(token -> "buyer.sandbox@paypal.com");

        AssetVerifier.VerifyResult result = verifier.confirm(
                asset,
                AssetVerificationMethod.PAYPAL_LOGIN,
                Map.of("accessToken", "sandbox-oauth-token"),
                ownerId,
                null
        );

        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo(VerificationProvider.SANDBOX);
        assertThat(result.evidence()).containsEntry("paypalEmail", "buyer.sandbox@paypal.com");
    }

    @Test
    @DisplayName("PAYPAL_LOGIN: dùng PAYPAL_LOGIN cho ví MoMo bị từ chối 400")
    void paypalLogin_nonPayPalPlatform_throws400() {
        DigitalAsset asset = eWalletAsset("MoMo", "0987654321");

        assertThatThrownBy(() -> verifier.start(asset, AssetVerificationMethod.PAYPAL_LOGIN, ownerId))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
