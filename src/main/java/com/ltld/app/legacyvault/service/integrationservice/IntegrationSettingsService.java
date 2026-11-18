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
 * Bật eKYC mô phỏng nghĩa là người thụ hưởng nhập số CCCD bất kỳ đúng định dạng là qua bước xác minh, nên:
 * <ul>
 *   <li>Admin chỉ BẬT được khi máy chủ cho phép ({@link IntegrationEnvironment#kycMockToggleAllowed()}, mặc định
 *       false trên bản thật). TẮT và "theo biến môi trường" thì luôn được.</li>
 *   <li>Máy chủ không cho phép thì lựa chọn "Bật" còn sót trong DB cũng bị bỏ qua (dùng biến môi trường).</li>
 *   <li>Mỗi lần bật thành công, SecurityAlertDetector tạo cảnh báo HIGH KYC_MOCK_ENABLED và mail cho mọi Admin.</li>
 * </ul>
 * Cờ được đọc lại từ DB tối đa {@link #CACHE_TTL_MS} một lần (MockKycVerifier gọi mỗi lần người thụ hưởng xác minh),
 * xoá cache sau khi Admin lưu (sau commit). Đọc DB lỗi thì dùng giá trị đã biết gần nhất, chưa có thì biến môi trường.
 */
@Slf4j
@Service
public class IntegrationSettingsService {

    static final long CACHE_TTL_MS = 30_000;
    static final int MIN_REASON_LENGTH = 5;
    /** Đoạn cố định trong detail audit khi eKYC mô phỏng chuyển từ tắt sang bật; SecurityAlertDetector dựa vào đây. */
    public static final String TURNED_ON_MARKER = "(effective false -> true)";

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
            c = effective(override);
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
        // @Size đếm cả khoảng trắng; audit lưu bản trim() nên kiểm lại độ dài sau khi trim
        String reason = req.reason() == null ? "" : req.reason().trim();
        if (reason.length() < MIN_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Reason must have at least " + MIN_REASON_LENGTH + " characters besides spaces.");
        }
        Boolean after = req.enabled();
        if (Boolean.TRUE.equals(after) && !env.kycMockToggleAllowed()) {
            throw new BusinessException(ErrorCode.INTEGRATION_SETTING_LOCKED,
                    "Simulated eKYC cannot be turned on from the admin page on this server.");
        }
        // Khoá dòng tới hết transaction: Admin thứ hai chờ, rồi so sánh với giá trị Admin thứ nhất vừa lưu
        IntegrationSetting row = repository.findByIdForUpdate(IntegrationSetting.SINGLETON_ID)
                .orElseGet(() -> IntegrationSetting.builder().id(IntegrationSetting.SINGLETON_ID).build());
        Boolean before = row.getKycMockEnabled();
        if (!Objects.equals(before, after)) {
            row.setKycMockEnabled(after);
            row.setUpdatedBy(adminId);
            row.setUpdatedAt(LocalDateTime.now(clock));
            repository.save(row);
            String email = userRepository.findById(adminId).map(User::getEmail).orElse(null);
            String detail = "kycMock " + label(before) + " -> " + label(after)
                    + " (effective " + effective(before) + " -> " + effective(after) + "); reason=" + reason;
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
                env.kycMockEnvDefault(), env.smsSandbox(), env.kycMockToggleAllowed(), row == null ? null : row.getUpdatedAt(), email);
        return new IntegrationsResponse(kyc, env.ai(), env.signature(), env.payments(), env.mail(), env.captcha(),
                env.googleLogin());
    }

    /** Giá trị có hiệu lực. "Bật" của Admin chỉ được tính khi máy chủ cho phép; không thì theo biến môi trường. */
    private boolean effective(Boolean override) {
        if (override == null) return env.kycMockEnvDefault();
        if (override) return env.kycMockToggleAllowed() || env.kycMockEnvDefault();
        return false;
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
