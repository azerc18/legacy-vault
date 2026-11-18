package com.ltld.app.legacyvault.service.integrationservice;

import com.ltld.app.legacyvault.dto.integrationdto.IntegrationsResponse;
import com.ltld.app.legacyvault.dto.integrationdto.UpdateKycMockRequest;
import com.ltld.app.legacyvault.entity.IntegrationSetting;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.repository.IntegrationSettingRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Trang "Tích hợp" của Admin.
 * <ul>
 *   <li>Cờ eKYC mô phỏng (trước đây chỉ có biến môi trường APP_KYC_MOCK_ENABLED): Admin bật / tắt ngay trên giao diện,
 *       hoặc chọn "theo biến môi trường". Biến môi trường vẫn là giá trị mặc định khi Admin chưa chọn.</li>
 *   <li>Trạng thái các dịch vụ bên ngoài (AI/OCR, ký xác nhận, thanh toán, email, CAPTCHA, đăng nhập Google), chỉ đọc,
 *       lấy từ {@link IntegrationEnvironment}. Không trả key / secret.</li>
 * </ul>
 * Cờ được đọc lại từ DB tối đa {@link #CACHE_TTL_MS} một lần (MockKycVerifier gọi mỗi lần người thụ hưởng xác minh),
 * xoá cache sau khi Admin lưu (sau commit). Đọc DB lỗi thì dùng giá trị đã biết gần nhất, chưa có thì biến môi trường.
 */
@Slf4j
@Service
public class IntegrationSettingsService {

    static final long CACHE_TTL_MS = 30_000;

    private final IntegrationSettingRepository repository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final IntegrationEnvironment env;
    private final Clock clock;

    private volatile Boolean cachedKycMock;
    private volatile long cacheLoadedAt;

    public IntegrationSettingsService(IntegrationSettingRepository repository, UserRepository userRepository,
                                      AuditLogService auditLogService, IntegrationEnvironment env, Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.env = env;
        this.clock = clock;
    }

    /** eKYC mô phỏng đang bật hay không: lựa chọn của Admin, chưa chọn thì theo APP_KYC_MOCK_ENABLED. */
    public boolean isKycMockEnabled() {
        Boolean c = cachedKycMock;
        long now = clock.millis();
        if (c != null && now - cacheLoadedAt < CACHE_TTL_MS) {
            return c;
        }
        try {
            Boolean override = repository.findById(IntegrationSetting.SINGLETON_ID)
                    .map(IntegrationSetting::getKycMockEnabled).orElse(null);
            c = override != null ? override : env.kycMockEnvDefault();
        } catch (RuntimeException e) {
            log.warn("Could not read integration settings, keeping the last known eKYC mock flag: {}",
                    e.getClass().getSimpleName());
            c = c != null ? c : env.kycMockEnvDefault();
        }
        cachedKycMock = c;
        cacheLoadedAt = now;
        return c;
    }

    @Transactional(readOnly = true)
    public IntegrationsResponse get() {
        return toResponse(repository.findById(IntegrationSetting.SINGLETON_ID).orElse(null));
    }

    @Transactional
    public IntegrationsResponse updateKycMock(UUID adminId, UpdateKycMockRequest req) {
        IntegrationSetting row = repository.findById(IntegrationSetting.SINGLETON_ID)
                .orElseGet(() -> IntegrationSetting.builder().id(IntegrationSetting.SINGLETON_ID).build());
        Boolean before = row.getKycMockEnabled();
        Boolean after = req.enabled();
        if (!Objects.equals(before, after)) {
            row.setKycMockEnabled(after);
            row.setUpdatedBy(adminId);
            row.setUpdatedAt(LocalDateTime.now(clock));
            repository.save(row);
            String email = userRepository.findById(adminId).map(User::getEmail).orElse(null);
            String detail = "kycMock " + label(before) + " -> " + label(after)
                    + " (effective " + effective(before) + " -> " + effective(after) + "); reason=" + req.reason().trim();
            auditLogService.log(AuditAction.INTEGRATION_SETTINGS_UPDATED, AuditResult.SUCCESS, adminId, email,
                    "IntegrationSetting", null, detail);
            log.info("eKYC mock changed by admin {}: {}", adminId, detail);
            invalidateCacheAfterCommit();
        }
        return toResponse(row);
    }

    // ------------------------------------------------------------------ nội bộ

    private IntegrationsResponse toResponse(IntegrationSetting row) {
        Boolean override = row == null ? null : row.getKycMockEnabled();
        String email = row == null || row.getUpdatedBy() == null ? null
                : userRepository.findById(row.getUpdatedBy()).map(User::getEmail).orElse(null);
        IntegrationsResponse.Kyc kyc = new IntegrationsResponse.Kyc(effective(override), override,
                env.kycMockEnvDefault(), env.smsSandbox(), row == null ? null : row.getUpdatedAt(), email);
        return new IntegrationsResponse(kyc, env.ai(), env.signature(), env.payments(), env.mail(), env.captcha(),
                env.googleLogin());
    }

    private boolean effective(Boolean override) {
        return override != null ? override : env.kycMockEnvDefault();
    }

    private String label(Boolean v) {
        return v == null ? "env(" + env.kycMockEnvDefault() + ")" : v.toString();
    }

    /** Như AiSettingsService: xoá cache SAU commit để lời gọi chen giữa không giữ lại giá trị cũ thêm 30 giây. */
    private void invalidateCacheAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cachedKycMock = null;
                }
            });
        } else {
            cachedKycMock = null;
        }
    }
}
