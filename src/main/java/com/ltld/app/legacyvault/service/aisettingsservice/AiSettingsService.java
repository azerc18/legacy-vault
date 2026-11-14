package com.ltld.app.legacyvault.service.aisettingsservice;

import com.ltld.app.legacyvault.dto.aisettingsdto.AiFeatureSettingResponse;
import com.ltld.app.legacyvault.dto.aisettingsdto.AiSettingsResponse;
import com.ltld.app.legacyvault.dto.aisettingsdto.UpdateAiFeatureSettingRequest;
import com.ltld.app.legacyvault.entity.AiFeatureSetting;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AiFeature;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.AiFeatureSettingRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Admin bật / tắt từng chức năng AI và chọn model chính / dự phòng trong danh sách cho phép.
 *
 * <p>API key KHÔNG đi qua đây: key vẫn chỉ đặt bằng biến môi trường (GEMINI_API_KEY, GEMINI_BACKUP_API_KEY),
 * API chỉ cho biết key đã được đặt hay chưa. Danh sách model cho phép = GEMINI_MODEL + GEMINI_BACKUP_MODEL +
 * GEMINI_ALLOWED_MODELS, nên Admin không gõ được tên model tuỳ ý.
 *
 * <p>Cấu hình được đọc lại từ DB tối đa {@link #CACHE_TTL_MS} một lần (mỗi lời gọi AI không phải truy vấn DB)
 * và làm mới ngay khi Admin lưu. Đọc DB lỗi thì dùng mặc định (bật, model của biến môi trường) để AI không
 * bị tắt ngoài ý muốn.
 */
@Slf4j
@Service
public class AiSettingsService {

    static final long CACHE_TTL_MS = 30_000;

    private final AiFeatureSettingRepository repository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    private final String provider;
    private final boolean apiKeyConfigured;
    private final boolean backupApiKeyConfigured;
    private final String defaultPrimary;
    private final String defaultBackup;
    private final List<String> allowedModels;

    private volatile Map<AiFeature, AiFeatureSetting> cache;
    private volatile long cacheLoadedAt;

    public AiSettingsService(AiFeatureSettingRepository repository,
                             UserRepository userRepository,
                             AuditLogService auditLogService,
                             Clock clock,
                             @Value("${app.ai.provider:mock}") String provider,
                             @Value("${app.ai.gemini.api-key:}") String apiKey,
                             @Value("${app.ai.gemini.backup-api-key:}") String backupApiKey,
                             @Value("${app.ai.gemini.model:gemini-3.6-flash}") String defaultPrimary,
                             @Value("${app.ai.gemini.backup-model:gemini-3.5-flash}") String defaultBackup,
                             @Value("${app.ai.gemini.allowed-models:}") String extraModels) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.provider = provider == null ? "mock" : provider.trim().toLowerCase(Locale.ROOT);
        this.apiKeyConfigured = apiKey != null && !apiKey.isBlank();
        this.backupApiKeyConfigured = backupApiKey != null && !backupApiKey.isBlank();
        this.defaultPrimary = blankToNull(defaultPrimary);
        this.defaultBackup = blankToNull(defaultBackup);
        this.allowedModels = buildAllowed(this.defaultPrimary, this.defaultBackup, extraModels);
    }

    static List<String> buildAllowed(String primary, String backup, String extra) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (primary != null) set.add(primary);
        if (backup != null) set.add(backup);
        if (extra != null) {
            for (String m : extra.split(",")) {
                String t = m.trim();
                if (!t.isEmpty()) set.add(t);
            }
        }
        return List.copyOf(set);
    }

    /** Cấu hình đang có hiệu lực của một chức năng; các dịch vụ AI gọi hàm này trước mỗi lần gọi Gemini. */
    public AiFeatureConfig forFeature(AiFeature feature) {
        AiFeatureSetting s = settings().get(feature);
        if (s == null) {
            return new AiFeatureConfig(true, defaultPrimary, defaultBackup);
        }
        return new AiFeatureConfig(s.isEnabled(), effective(s.getPrimaryModel(), defaultPrimary),
                effective(s.getBackupModel(), defaultBackup));
    }

    @Transactional(readOnly = true)
    public AiSettingsResponse get() {
        Map<AiFeature, AiFeatureSetting> all = load();
        Map<UUID, String> emails = new HashMap<>();
        List<AiFeatureSettingResponse> rows = Arrays.stream(AiFeature.values())
                .map(f -> toResponse(f, all.get(f), emails))
                .toList();
        return new AiSettingsResponse(provider, apiKeyConfigured, backupApiKeyConfigured,
                defaultPrimary, defaultBackup, allowedModels, rows);
    }

    @Transactional
    public AiFeatureSettingResponse update(UUID adminId, AiFeature feature, UpdateAiFeatureSettingRequest req) {
        String primary = blankToNull(req.primaryModel());
        String backup = blankToNull(req.backupModel());
        requireAllowed(primary);
        requireAllowed(backup);
        String effPrimary = effective(primary, defaultPrimary);
        String effBackup = effective(backup, defaultBackup);
        if (effPrimary != null && effPrimary.equalsIgnoreCase(effBackup)) {
            throw new BusinessException(ErrorCode.AI_MODEL_NOT_ALLOWED,
                    "The backup model must be different from the main model.");
        }

        AiFeatureSetting s = repository.findById(feature)
                .orElseGet(() -> AiFeatureSetting.builder().feature(feature).build());
        List<String> changes = new ArrayList<>();
        if (s.isEnabled() != req.enabled()) {
            changes.add("enabled " + s.isEnabled() + " -> " + req.enabled());
        }
        if (!Objects.equals(s.getPrimaryModel(), primary)) {
            changes.add("primary " + label(s.getPrimaryModel()) + " -> " + label(primary));
        }
        if (!Objects.equals(s.getBackupModel(), backup)) {
            changes.add("backup " + label(s.getBackupModel()) + " -> " + label(backup));
        }
        if (!changes.isEmpty()) {
            s.setEnabled(req.enabled());
            s.setPrimaryModel(primary);
            s.setBackupModel(backup);
            s.setUpdatedBy(adminId);
            s.setUpdatedAt(LocalDateTime.now(clock));
            repository.save(s);
            String email = userRepository.findById(adminId).map(User::getEmail).orElse(null);
            auditLogService.log(AuditAction.AI_SETTINGS_UPDATED, AuditResult.SUCCESS, adminId, email,
                    "AiFeatureSetting", feature.name(), feature.name() + ": " + String.join("; ", changes));
            log.info("AI settings for {} changed by admin {}: {}", feature, adminId, changes);
            cache = null;
        }
        return toResponse(feature, s, new HashMap<>());
    }

    // ------------------------------------------------------------------ nội bộ

    private Map<AiFeature, AiFeatureSetting> settings() {
        Map<AiFeature, AiFeatureSetting> c = cache;
        long now = clock.millis();
        if (c != null && now - cacheLoadedAt < CACHE_TTL_MS) {
            return c;
        }
        try {
            c = load();
        } catch (RuntimeException e) {
            log.warn("Could not read AI settings, using defaults: {}", e.getClass().getSimpleName());
            c = c != null ? c : Map.of();
        }
        cache = c;
        cacheLoadedAt = now;
        return c;
    }

    private Map<AiFeature, AiFeatureSetting> load() {
        Map<AiFeature, AiFeatureSetting> m = new EnumMap<>(AiFeature.class);
        repository.findAll().forEach(s -> m.put(s.getFeature(), s));
        return m;
    }

    /** Model đã lưu nhưng nay không còn trong danh sách cho phép (đổi biến môi trường) thì về mặc định. */
    private String effective(String chosen, String fallback) {
        if (chosen == null) return fallback;
        return allowedModels.contains(chosen) ? chosen : fallback;
    }

    private void requireAllowed(String model) {
        if (model != null && !allowedModels.contains(model)) {
            throw new BusinessException(ErrorCode.AI_MODEL_NOT_ALLOWED,
                    "This model is not in the allowed list.");
        }
    }

    private AiFeatureSettingResponse toResponse(AiFeature f, AiFeatureSetting s, Map<UUID, String> emails) {
        if (s == null) {
            return new AiFeatureSettingResponse(f.name(), true, defaultPrimary, defaultBackup, null, null, null, null);
        }
        String email = s.getUpdatedBy() == null ? null : emails.computeIfAbsent(s.getUpdatedBy(),
                id -> userRepository.findById(id).map(User::getEmail).orElse(null));
        return new AiFeatureSettingResponse(f.name(), s.isEnabled(),
                effective(s.getPrimaryModel(), defaultPrimary), effective(s.getBackupModel(), defaultBackup),
                s.getPrimaryModel(), s.getBackupModel(), s.getUpdatedAt(), email);
    }

    private static String label(String model) {
        return model == null ? "default" : model;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
