package com.ltld.app.legacyvault.service.integrationservice;

import com.ltld.app.legacyvault.dto.integrationdto.IntegrationsResponse;
import com.ltld.app.legacyvault.dto.integrationdto.UpdateKycMockRequest;
import com.ltld.app.legacyvault.entity.IntegrationSetting;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.IntegrationSettingRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Trang Tích hợp: cờ eKYC mô phỏng từ DB (mặc định theo biến môi trường), có cache, có audit, không lộ key. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IntegrationSettingsServiceTest {

    @Mock private IntegrationSettingRepository repository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;

    private final UUID adminId = UUID.randomUUID();
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-18T05:00:00Z"));
    private IntegrationSetting row;

    @BeforeEach
    void setUp() {
        row = IntegrationSetting.builder().id(IntegrationSetting.SINGLETON_ID).build();   // = seed: chưa chọn
        when(repository.findById(IntegrationSetting.SINGLETON_ID)).thenAnswer(inv -> Optional.of(row));
        when(repository.findByIdForUpdate(IntegrationSetting.SINGLETON_ID)).thenAnswer(inv -> Optional.of(row));
        when(repository.save(any(IntegrationSetting.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(adminId))
                .thenReturn(Optional.of(User.builder().id(adminId).email("admin@example.test").build()));
    }

    /** Máy chủ cho Admin bật (như local / docker). */
    private IntegrationSettingsService service(boolean envDefault) {
        return service(envDefault, true);
    }

    private IntegrationSettingsService service(boolean envDefault, boolean toggleAllowed) {
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        return new IntegrationSettingsService(repository, userRepository, auditLogService,
                env(envDefault, toggleAllowed), clock);
    }

    static IntegrationEnvironment env(boolean kycMockDefault, boolean toggleAllowed) {
        return new IntegrationEnvironment(kycMockDefault, toggleAllowed, true, "gemini", "gemini-secret-value", "", "groq-secret-value", "",
                false, "TMNCODE1", "vnpay-hash-secret", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "", "", "https://api-m.sandbox.paypal.com", true,
                "smtp.gmail.com", "someone@gmail.com", "mail-app-password", true, "site-key", "captcha-secret", "");
    }

    @Test
    @DisplayName("Admin chưa chọn -> theo biến môi trường (hành vi cũ)")
    void noOverride_followsEnv() {
        assertThat(service(false).isKycMockEnabled()).isFalse();
        assertThat(service(true).isKycMockEnabled()).isTrue();
    }

    @Test
    @DisplayName("Admin đã chọn -> ghi đè biến môi trường, cả hai chiều")
    void override_winsOverEnv() {
        row.setKycMockEnabled(true);
        assertThat(service(false).isKycMockEnabled()).isTrue();
        row.setKycMockEnabled(false);
        assertThat(service(true).isKycMockEnabled()).isFalse();
    }

    @Test
    @DisplayName("Đọc cờ có cache 30 giây, hết hạn thì đọc lại DB")
    void flag_isCached() {
        IntegrationSettingsService s = service(false);
        s.isKycMockEnabled();
        s.isKycMockEnabled();
        verify(repository, times(1)).findById(IntegrationSetting.SINGLETON_ID);
        now.set(now.get().plusMillis(IntegrationSettingsService.CACHE_TTL_MS + 1));
        s.isKycMockEnabled();
        verify(repository, times(2)).findById(IntegrationSetting.SINGLETON_ID);
    }

    @Test
    @DisplayName("DB lỗi -> giữ giá trị đã biết gần nhất; chưa từng đọc được thì theo biến môi trường")
    void dbError_keepsLastKnownThenEnv() {
        IntegrationSettingsService s = service(false);
        row.setKycMockEnabled(true);
        assertThat(s.isKycMockEnabled()).isTrue();
        when(repository.findById(IntegrationSetting.SINGLETON_ID)).thenThrow(new IllegalStateException("db down"));
        now.set(now.get().plusMillis(IntegrationSettingsService.CACHE_TTL_MS + 1));
        assertThat(s.isKycMockEnabled()).isTrue();

        assertThat(service(false).isKycMockEnabled()).isFalse();
    }

    @Test
    @DisplayName("Admin bật mock -> lưu, người sửa + thời điểm, audit có giá trị trước/sau và lý do, cache đổi ngay")
    void update_savesAuditsAndRefreshes() {
        IntegrationSettingsService s = service(false);
        assertThat(s.isKycMockEnabled()).isFalse();

        IntegrationsResponse res = s.updateKycMock(adminId, new UpdateKycMockRequest(true, "  Demo cho hội đồng  "));

        assertThat(row.getKycMockEnabled()).isTrue();
        assertThat(row.getUpdatedBy()).isEqualTo(adminId);
        assertThat(row.getUpdatedAt()).isNotNull();
        assertThat(res.kyc().mockEnabled()).isTrue();
        assertThat(res.kyc().mockOverride()).isTrue();
        assertThat(res.kyc().envDefault()).isFalse();
        assertThat(res.kyc().updatedByEmail()).isEqualTo("admin@example.test");
        assertThat(res.kyc().toggleAllowed()).isTrue();
        assertThat(s.isKycMockEnabled()).isTrue();
        // review n2: đọc dòng bằng SELECT ... FOR UPDATE khi lưu
        verify(repository).findByIdForUpdate(IntegrationSetting.SINGLETON_ID);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).log(eq(AuditAction.INTEGRATION_SETTINGS_UPDATED), eq(AuditResult.SUCCESS), eq(adminId),
                eq("admin@example.test"), eq("IntegrationSetting"), isNull(), detail.capture());
        assertThat(detail.getValue()).isEqualTo("kycMock env(false) -> true (effective false -> true); reason=Demo cho hội đồng");
    }

    @Test
    @DisplayName("Quay về 'theo biến môi trường' (null) cũng được lưu và audit")
    void update_backToEnv() {
        row.setKycMockEnabled(true);

        IntegrationsResponse res = service(false).updateKycMock(adminId, new UpdateKycMockRequest(null, "Hết buổi demo"));

        assertThat(row.getKycMockEnabled()).isNull();
        assertThat(res.kyc().mockEnabled()).isFalse();
        assertThat(res.kyc().mockOverride()).isNull();
        verify(auditLogService).log(eq(AuditAction.INTEGRATION_SETTINGS_UPDATED), any(), any(), any(), any(), any(),
                eq("kycMock true -> env(false) (effective true -> false); reason=Hết buổi demo"));
    }

    @Test
    @DisplayName("Chọn y như đang có -> không ghi DB, không audit")
    void update_noChange_noWrite() {
        service(false).updateKycMock(adminId, new UpdateKycMockRequest(null, "Không đổi gì"));

        verify(repository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("Trạng thái trả về không chứa bất kỳ key / secret / tài khoản email nào")
    void get_neverLeaksSecrets() {
        IntegrationsResponse res = service(false).get();

        String all = res.toString();
        for (String secret : new String[]{"gemini-secret-value", "groq-secret-value", "TMNCODE1", "vnpay-hash-secret",
                "someone@gmail.com", "mail-app-password", "site-key", "captcha-secret"}) {
            assertThat(all).doesNotContain(secret);
        }
        assertThat(res.ai().geminiKeyConfigured()).isTrue();
        assertThat(res.ai().groqKeyConfigured()).isTrue();
        assertThat(res.ai().deepseekKeyConfigured()).isFalse();
        assertThat(res.payments().vnpay()).isEqualTo(new IntegrationsResponse.Gateway(true, true));
        assertThat(res.payments().paypal().configured()).isFalse();
        assertThat(res.mail()).isEqualTo(new IntegrationsResponse.Mail("smtp.gmail.com", true));
        assertThat(res.captcha()).isEqualTo(new IntegrationsResponse.Captcha(true, true, true));
        assertThat(res.googleLogin().configured()).isFalse();
        assertThat(res.signature().caProviderIntegrated()).isFalse();
    }

    @Test
    @DisplayName("URL cổng thanh toán: có 'sandbox' hoặc để trống = sandbox; URL thật = live")
    void sandboxDetection() {
        assertThat(IntegrationEnvironment.isSandbox("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html")).isTrue();
        assertThat(IntegrationEnvironment.isSandbox("https://api-m.sandbox.paypal.com")).isTrue();
        assertThat(IntegrationEnvironment.isSandbox("")).isTrue();
        assertThat(IntegrationEnvironment.isSandbox("https://pay.vnpay.vn/vpcpay.html")).isFalse();
        assertThat(IntegrationEnvironment.isSandbox("https://api-m.paypal.com")).isFalse();
    }

    // ------------------------------------------------------------------ review M1: bản thật không bật được từ trang Admin

    @Test
    @DisplayName("Máy chủ không cho bật (bản thật) -> chọn 'Bật' bị chặn 409 INTEGRATION_SETTING_LOCKED, không ghi DB, không audit")
    void turnOn_whenToggleNotAllowed_isRejected() {
        IntegrationSettingsService s = service(false, false);

        assertThatThrownBy(() -> s.updateKycMock(adminId, new UpdateKycMockRequest(true, "Demo cho hội đồng")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INTEGRATION_SETTING_LOCKED);
        assertThat(row.getKycMockEnabled()).isNull();
        verify(repository, never()).save(any());
        verifyNoInteractions(auditLogService);
        assertThat(s.isKycMockEnabled()).isFalse();
    }

    @Test
    @DisplayName("Máy chủ không cho bật -> TẮT và 'theo biến môi trường' vẫn lưu được (an toàn hơn)")
    void turnOff_whenToggleNotAllowed_isAllowed() {
        IntegrationsResponse res = service(true, false).updateKycMock(adminId, new UpdateKycMockRequest(false, "Tắt sau demo"));

        assertThat(row.getKycMockEnabled()).isFalse();
        assertThat(res.kyc().mockEnabled()).isFalse();
        assertThat(res.kyc().toggleAllowed()).isFalse();
        verify(auditLogService).log(eq(AuditAction.INTEGRATION_SETTINGS_UPDATED), any(), any(), any(), any(), any(),
                eq("kycMock env(true) -> false (effective true -> false); reason=Tắt sau demo"));
    }

    @Test
    @DisplayName("Máy chủ không cho bật -> 'Bật' còn sót trong DB (sửa tay / đổi cấu hình) bị bỏ qua, theo biến môi trường")
    void storedOn_isIgnored_whenToggleNotAllowed() {
        row.setKycMockEnabled(true);

        assertThat(service(false, false).isKycMockEnabled()).isFalse();
        assertThat(service(false, false).get().kyc().mockEnabled()).isFalse();
        assertThat(service(true, false).isKycMockEnabled()).isTrue();    // biến môi trường bật thì vẫn bật
    }

    @Test
    @DisplayName("Review n1: lý do chỉ có khoảng trắng / dưới 5 ký tự sau trim -> VALIDATION_FAILED, không lưu")
    void reason_isCheckedAfterTrim() {
        IntegrationSettingsService s = service(false);

        assertThatThrownBy(() -> s.updateKycMock(adminId, new UpdateKycMockRequest(false, "a    ")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> s.updateKycMock(adminId, new UpdateKycMockRequest(false, "      ")))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("Bật thành công -> detail audit có đoạn đánh dấu mà SecurityAlertDetector dùng để tạo cảnh báo HIGH")
    void turnOn_auditDetailCarriesAlertMarker() {
        service(false).updateKycMock(adminId, new UpdateKycMockRequest(true, "Demo buổi sáng"));

        verify(auditLogService).log(eq(AuditAction.INTEGRATION_SETTINGS_UPDATED), any(), any(), any(), any(), any(),
                argThat((String d) -> d.startsWith("kycMock ") && d.contains(IntegrationSettingsService.TURNED_ON_MARKER)));
    }
}
