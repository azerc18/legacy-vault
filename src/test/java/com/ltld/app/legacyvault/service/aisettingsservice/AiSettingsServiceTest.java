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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiSettingsServiceTest {

    private static final String PRIMARY = "gemini-3.6-flash";
    private static final String BACKUP = "gemini-3.5-flash";
    private static final String EXTRA = "gemini-3.6-pro";

    @Mock private AiFeatureSettingRepository repository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;

    private final UUID adminId = UUID.randomUUID();
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-15T00:00:00Z"));
    private AiSettingsService service;

    @BeforeEach
    void setUp() {
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        service = new AiSettingsService(repository, userRepository, auditLogService, clock,
                "gemini", "secret-key-value", "", PRIMARY, BACKUP, " gemini-3.6-pro , ," + PRIMARY);
        User admin = new User();
        admin.setEmail("admin@legacyvault.local");
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static AiFeatureSetting row(AiFeature f, boolean enabled, String primary, String backup) {
        return AiFeatureSetting.builder().feature(f).enabled(enabled).primaryModel(primary).backupModel(backup).build();
    }

    @Test
    void allowedModels_areEnvModelsPlusExtraList_withoutDuplicatesOrBlanks() {
        when(repository.findAll()).thenReturn(List.of());

        AiSettingsResponse res = service.get();

        assertThat(res.allowedModels()).containsExactly(PRIMARY, BACKUP, EXTRA);
        assertThat(res.apiKeyConfigured()).isTrue();
        assertThat(res.backupApiKeyConfigured()).isFalse();
        assertThat(res.features()).extracting(AiFeatureSettingResponse::feature)
                .containsExactly("ASSISTANT", "DEATH_CERTIFICATE_OCR", "CITIZEN_ID_OCR");
    }

    @Test
    void response_neverContainsTheApiKey() {
        when(repository.findAll()).thenReturn(List.of(row(AiFeature.ASSISTANT, true, EXTRA, null)));

        assertThat(service.get().toString()).doesNotContain("secret-key-value");
    }

    @Test
    void forFeature_defaultsToEnabledWithEnvModels_whenNoRow() {
        when(repository.findAll()).thenReturn(List.of());

        assertThat(service.forFeature(AiFeature.CITIZEN_ID_OCR))
                .isEqualTo(new AiFeatureConfig(true, PRIMARY, BACKUP));
    }

    @Test
    void forFeature_usesAdminChoice_andFallsBackWhenStoredModelNoLongerAllowed() {
        when(repository.findAll()).thenReturn(List.of(
                row(AiFeature.ASSISTANT, false, EXTRA, PRIMARY),
                row(AiFeature.DEATH_CERTIFICATE_OCR, true, "gemini-removed", null)));

        assertThat(service.forFeature(AiFeature.ASSISTANT)).isEqualTo(new AiFeatureConfig(false, EXTRA, PRIMARY));
        assertThat(service.forFeature(AiFeature.DEATH_CERTIFICATE_OCR)).isEqualTo(new AiFeatureConfig(true, PRIMARY, BACKUP));
    }

    @Test
    void forFeature_cachesReads_andKeepsDefaultsWhenDbFails() {
        when(repository.findAll()).thenReturn(List.of(row(AiFeature.ASSISTANT, false, null, null)));
        service.forFeature(AiFeature.ASSISTANT);
        service.forFeature(AiFeature.ASSISTANT);
        verify(repository, times(1)).findAll();

        now.set(now.get().plusMillis(AiSettingsService.CACHE_TTL_MS + 1));
        when(repository.findAll()).thenThrow(new RuntimeException("db down"));
        // Lỗi DB: giữ bản đã đọc trước đó thay vì bật lại / ném lỗi
        assertThat(service.forFeature(AiFeature.ASSISTANT).enabled()).isFalse();
    }

    @Test
    void update_savesChange_writesAudit_andRefreshesCache() {
        AiFeatureSetting existing = row(AiFeature.ASSISTANT, true, null, null);
        when(repository.findAll()).thenReturn(List.of(existing));
        when(repository.findById(AiFeature.ASSISTANT)).thenReturn(Optional.of(existing));
        service.forFeature(AiFeature.ASSISTANT);

        AiFeatureSettingResponse res = service.update(adminId, AiFeature.ASSISTANT,
                new UpdateAiFeatureSettingRequest(false, EXTRA, ""));

        assertThat(res.enabled()).isFalse();
        assertThat(res.primaryModel()).isEqualTo(EXTRA);
        assertThat(res.backupModel()).isEqualTo(BACKUP);
        assertThat(res.backupOverride()).isNull();
        assertThat(existing.getUpdatedBy()).isEqualTo(adminId);
        verify(auditLogService).log(eq(AuditAction.AI_SETTINGS_UPDATED), eq(AuditResult.SUCCESS), eq(adminId),
                eq("admin@legacyvault.local"), eq("AiFeatureSetting"), isNull(),
                eq("ASSISTANT: enabled true -> false; primary default -> " + EXTRA));
        // Cache bị xoá khi lưu: lần đọc sau truy vấn lại DB
        service.forFeature(AiFeature.ASSISTANT);
        verify(repository, times(2)).findAll();
    }

    @Test
    void update_withoutChange_doesNotSaveOrAudit() {
        AiFeatureSetting existing = row(AiFeature.CITIZEN_ID_OCR, true, null, null);
        when(repository.findById(AiFeature.CITIZEN_ID_OCR)).thenReturn(Optional.of(existing));

        service.update(adminId, AiFeature.CITIZEN_ID_OCR, new UpdateAiFeatureSettingRequest(true, null, null));

        verify(repository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void update_rejectsModelOutsideAllowedList() {
        assertThatThrownBy(() -> service.update(adminId, AiFeature.ASSISTANT,
                new UpdateAiFeatureSettingRequest(true, "gpt-5", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_MODEL_NOT_ALLOWED);
        verify(repository, never()).save(any());
    }

    @Test
    void update_rejectsBackupEqualToPrimary_includingDefaults() {
        assertThatThrownBy(() -> service.update(adminId, AiFeature.ASSISTANT,
                new UpdateAiFeatureSettingRequest(true, BACKUP, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_MODEL_NOT_ALLOWED);
    }

    // Review #290 M1: chạy AuditLogServiceImpl THẬT (không mock) để bắt lỗi targetId không phải UUID
    @Test
    void update_withRealAuditLogService_publishesAuditEventWithoutTargetId() {
        org.springframework.context.ApplicationEventPublisher publisher =
                mock(org.springframework.context.ApplicationEventPublisher.class);
        AiSettingsService real = new AiSettingsService(repository, userRepository,
                new com.ltld.app.legacyvault.service.auditservice.AuditLogServiceImpl(publisher),
                Clock.systemUTC(), "gemini", "k", "", PRIMARY, BACKUP, EXTRA);
        when(repository.findById(AiFeature.ASSISTANT)).thenReturn(Optional.of(row(AiFeature.ASSISTANT, true, null, null)));

        real.update(adminId, AiFeature.ASSISTANT, new UpdateAiFeatureSettingRequest(false, null, null));

        org.mockito.ArgumentCaptor<com.ltld.app.legacyvault.service.auditservice.AuditEvent> event =
                org.mockito.ArgumentCaptor.forClass(com.ltld.app.legacyvault.service.auditservice.AuditEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AuditAction.AI_SETTINGS_UPDATED);
        assertThat(event.getValue().entityType()).isEqualTo("AiFeatureSetting");
        verify(repository).save(any());
    }

    // Review #290 m1: trong transaction, cache chỉ bị xoá sau commit
    @Test
    void update_insideTransaction_keepsCacheUntilAfterCommit() {
        AiFeatureSetting existing = row(AiFeature.ASSISTANT, true, null, null);
        when(repository.findAll()).thenReturn(List.of(existing));
        when(repository.findById(AiFeature.ASSISTANT)).thenReturn(Optional.of(existing));
        service.forFeature(AiFeature.ASSISTANT);

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.update(adminId, AiFeature.ASSISTANT, new UpdateAiFeatureSettingRequest(false, null, null));
            service.forFeature(AiFeature.ASSISTANT);
            verify(repository, times(1)).findAll();   // chưa commit: vẫn dùng cache

            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
        service.forFeature(AiFeature.ASSISTANT);
        verify(repository, times(2)).findAll();       // sau commit: đọc lại DB
    }
}
