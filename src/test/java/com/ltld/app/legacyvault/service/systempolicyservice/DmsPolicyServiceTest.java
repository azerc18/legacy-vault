package com.ltld.app.legacyvault.service.systempolicyservice;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigRequest;
import com.ltld.app.legacyvault.dto.systempolicydto.AdminDmsPolicyResponse;
import com.ltld.app.legacyvault.dto.systempolicydto.DmsPolicy;
import com.ltld.app.legacyvault.dto.systempolicydto.DmsPolicyImpactResponse;
import com.ltld.app.legacyvault.dto.systempolicydto.UpdateDmsPolicyRequest;
import com.ltld.app.legacyvault.dto.systempolicydto.UpdateDmsPolicyRequest.RangeInput;
import com.ltld.app.legacyvault.entity.DmsPolicySetting;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.DmsPolicySettingRepository;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Chính sách hệ thống (DMS): đọc có cache, kiểm cấu hình của Owner, Admin lưu có kiểm và có audit. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DmsPolicyServiceTest {

    @Mock private DmsPolicySettingRepository repository;
    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;

    private final UUID adminId = UUID.randomUUID();
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-18T03:00:00Z"));
    private DmsPolicySetting row;
    private DmsPolicyService service;

    @BeforeEach
    void setUp() {
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        service = new DmsPolicyService(repository, dmsConfigRepository, userRepository, auditLogService, clock);
        row = DmsPolicySetting.builder().id(DmsPolicySetting.SINGLETON_ID).build();   // = seed của migration
        when(repository.findById(DmsPolicySetting.SINGLETON_ID)).thenAnswer(inv -> Optional.of(row));
        when(repository.findByIdForUpdate(DmsPolicySetting.SINGLETON_ID)).thenAnswer(inv -> Optional.of(row));
        when(repository.save(any(DmsPolicySetting.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(adminId))
                .thenReturn(Optional.of(User.builder().id(adminId).email("admin@example.test").build()));
    }

    private static UpdateDmsPolicyRequest req(int imin, int idef, int imax, int gmin, int gdef, int gmax,
                                              int rmin, int rdef, int rmax, int backupWait) {
        return new UpdateDmsPolicyRequest(new RangeInput(imin, idef, imax), new RangeInput(gmin, gdef, gmax),
                new RangeInput(rmin, rdef, rmax), backupWait);
    }

    private static DmsConfigRequest ownerReq(int interval, int grace, int reminders) {
        DmsConfigRequest r = new DmsConfigRequest();
        r.setCheckinIntervalDays(interval);
        r.setGracePeriodDays(grace);
        r.setReminderCount(reminders);
        return r;
    }

    @Test
    @DisplayName("Seed của migration = đúng các số trước đây viết cứng (30/7/3, 1–365/1–90/1–10, chờ 14 ngày)")
    void seededRow_equalsOldHardcodedDefaults() {
        assertThat(service.current()).isEqualTo(DmsPolicy.DEFAULTS);
    }

    @Test
    @DisplayName("current() dùng cache 30 giây, hết hạn thì đọc lại DB")
    void current_isCachedForTtl() {
        service.current();
        service.current();
        verify(repository, times(1)).findById(DmsPolicySetting.SINGLETON_ID);

        now.set(now.get().plusMillis(DmsPolicyService.CACHE_TTL_MS + 1));
        service.current();
        verify(repository, times(2)).findById(DmsPolicySetting.SINGLETON_ID);
    }

    @Test
    @DisplayName("Đọc DB lỗi -> dùng mặc định cũ, không làm hỏng việc lưu DMS của Owner")
    void current_dbError_fallsBackToDefaults() {
        when(repository.findById(DmsPolicySetting.SINGLETON_ID)).thenThrow(new IllegalStateException("db down"));

        assertThat(service.current()).isEqualTo(DmsPolicy.DEFAULTS);
    }

    @Test
    @DisplayName("Owner lưu DMS ngoài khoảng Admin thu hẹp -> DMS_POLICY_OUT_OF_RANGE, thông báo nêu ô và khoảng")
    void requireWithinPolicy_outside_throws() {
        row.setCheckinIntervalMinDays(7);
        row.setCheckinIntervalMaxDays(180);

        assertThatThrownBy(() -> service.requireWithinPolicy(ownerReq(200, 7, 3)))
                .isInstanceOf(DmsException.class)
                .hasMessageContaining("Check-in interval")
                .hasMessageContaining("7 and 180 days")
                .extracting("errorCode").isEqualTo(ErrorCode.DMS_POLICY_OUT_OF_RANGE);
        assertThatThrownBy(() -> service.requireWithinPolicy(ownerReq(3, 7, 3)))
                .extracting("errorCode").isEqualTo(ErrorCode.DMS_POLICY_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("Owner lưu đúng ở hai đầu mút của khoảng -> được")
    void requireWithinPolicy_boundaries_pass() {
        row.setReminderCountMin(2);
        row.setReminderCountMax(5);

        assertThatCode(() -> service.requireWithinPolicy(ownerReq(30, 7, 2))).doesNotThrowAnyException();
        assertThatCode(() -> service.requireWithinPolicy(ownerReq(30, 7, 5))).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireWithinPolicy(ownerReq(30, 7, 6)))
                .hasMessageContaining("Reminder count must be between 2 and 5 (system policy)");
    }

    @Test
    @DisplayName("Admin lưu: min > max, mặc định ngoài khoảng, vượt giới hạn cứng, số ngày chờ 0 -> SYSTEM_POLICY_INVALID, không lưu")
    void update_invalid_rejected() {
        assertInvalid(req(100, 50, 60, 1, 7, 90, 1, 3, 10, 14), "minimum is greater than maximum");
        assertInvalid(req(1, 400, 365, 1, 7, 90, 1, 3, 10, 14), "default value");
        assertInvalid(req(1, 30, 400, 1, 7, 90, 1, 3, 10, 14), "within 1–365");
        assertInvalid(req(1, 30, 365, 0, 7, 90, 1, 3, 10, 14), "within 1–90");
        assertInvalid(req(1, 30, 365, 1, 7, 90, 1, 3, 11, 14), "within 1–10");
        assertInvalid(req(1, 30, 365, 1, 7, 90, 1, 3, 10, 0), "Backup executor wait");
        assertInvalid(req(1, 30, 365, 1, 7, 90, 1, 3, 10, 91), "Backup executor wait");
        verify(repository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    private void assertInvalid(UpdateDmsPolicyRequest r, String message) {
        assertThatThrownBy(() -> service.update(adminId, r))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(message)
                .extracting("errorCode").isEqualTo(ErrorCode.SYSTEM_POLICY_INVALID);
    }

    @Test
    @DisplayName("Admin lưu hợp lệ -> ghi DB, người sửa + thời điểm, audit SYSTEM_POLICY_UPDATED chỉ liệt kê ô đã đổi")
    void update_valid_savesAndAudits() {
        when(dmsConfigRepository.countRunningOutsidePolicy(any(), eq(7), eq(180), eq(1), eq(90), eq(1), eq(10)))
                .thenReturn(2L);

        AdminDmsPolicyResponse res = service.update(adminId, req(7, 30, 180, 1, 7, 90, 1, 3, 10, 21));

        assertThat(row.getCheckinIntervalMinDays()).isEqualTo(7);
        assertThat(row.getCheckinIntervalMaxDays()).isEqualTo(180);
        assertThat(row.getBackupActivationWaitDays()).isEqualTo(21);
        assertThat(row.getUpdatedBy()).isEqualTo(adminId);
        assertThat(row.getUpdatedAt()).isNotNull();
        assertThat(res.policy().checkinIntervalDays()).isEqualTo(new DmsPolicy.Range(7, 30, 180));
        assertThat(res.configsOutsideLimits()).isEqualTo(2L);
        assertThat(res.updatedByEmail()).isEqualTo("admin@example.test");
        // n1: đọc dòng bằng SELECT ... FOR UPDATE, không dùng findById thường
        verify(repository).findByIdForUpdate(DmsPolicySetting.SINGLETON_ID);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).log(eq(AuditAction.SYSTEM_POLICY_UPDATED), eq(AuditResult.SUCCESS), eq(adminId),
                eq("admin@example.test"), eq("DmsPolicySetting"), isNull(), detail.capture());
        assertThat(detail.getValue())
                .contains("checkinIntervalDays 1..365 (default 30) -> 7..180 (default 30)")
                .contains("backupActivationWaitDays 14 -> 21")
                .doesNotContain("gracePeriodDays").doesNotContain("reminderCount");
    }

    @Test
    @DisplayName("Lưu xong (không có transaction) -> current() đọc giá trị mới ngay, không chờ hết cache")
    void update_invalidatesCache() {
        service.current();   // nạp cache với giá trị cũ

        service.update(adminId, req(1, 30, 365, 1, 7, 90, 2, 4, 6, 14));

        assertThat(service.current().reminderCount()).isEqualTo(new DmsPolicy.Range(2, 4, 6));
    }

    @Test
    @DisplayName("Lưu y nguyên -> không ghi DB, không audit")
    void update_noChange_noWrite() {
        service.update(adminId, req(1, 30, 365, 1, 7, 90, 1, 3, 10, 14));

        verify(repository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("Xem trước: đếm DMS ngoài khoảng MỚI (chưa lưu), không ghi DB, không audit, không khoá dòng")
    void previewImpact_countsAgainstDraft_withoutWriting() {
        when(dmsConfigRepository.countRunningOutsidePolicy(any(), eq(14), eq(90), eq(3), eq(30), eq(2), eq(5)))
                .thenReturn(5L);

        DmsPolicyImpactResponse res = service.previewImpact(req(14, 30, 90, 3, 7, 30, 2, 3, 5, 14));

        assertThat(res.configsOutsideLimits()).isEqualTo(5L);
        assertThat(row.getCheckinIntervalMinDays()).isEqualTo(1);   // dòng DB không đổi
        verify(repository, never()).save(any());
        verify(repository, never()).findByIdForUpdate(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("Xem trước với khoảng sai -> SYSTEM_POLICY_INVALID như khi lưu, không đếm")
    void previewImpact_invalid_rejected() {
        assertThatThrownBy(() -> service.previewImpact(req(100, 50, 60, 1, 7, 90, 1, 3, 10, 14)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SYSTEM_POLICY_INVALID);
        verifyNoInteractions(dmsConfigRepository);
    }
}
