package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.entity.SecurityAlert;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.SecurityAlertRule;
import com.ltld.app.legacyvault.enums.SecurityAlertSeverity;
import com.ltld.app.legacyvault.enums.SecurityAlertStatus;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
import com.ltld.app.legacyvault.repository.SecurityAlertRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityAlertDetectorTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private SecurityAlertRepository alertRepository;
    @Mock private UserRepository userRepository;
    @Mock private ExecutorNotificationService notificationService;

    @InjectMocks private SecurityAlertDetector detector;

    private final Instant now = Instant.parse("2026-10-27T10:00:00Z");
    private final User admin = User.builder().id(UUID.randomUUID()).email("admin@example.test").build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(detector, "loginFailuresPerIp", 10L);
        ReflectionTestUtils.setField(detector, "loginFailureWindow", Duration.ofMinutes(15));
        ReflectionTestUtils.setField(detector, "downloadsPerUser", 20L);
        ReflectionTestUtils.setField(detector, "downloadWindow", Duration.ofMinutes(10));
    }

    private static AuditLogRepository.IpCount ipCount(String ip, long total) {
        return new AuditLogRepository.IpCount() {
            public String getIp() { return ip; }
            public Long getTotal() { return total; }
        };
    }

    @Test
    void loginFailuresFromIp_createsHighAlert_andMailsAdmins() {
        when(auditLogRepository.countByIpSince(eq(AuditAction.LOGIN_FAILED), any(), eq(10L)))
                .thenReturn(List.of(ipCount("1.2.3.4", 12)));
        when(alertRepository.findFirstByRuleAndSubjectAndStatus(
                SecurityAlertRule.LOGIN_FAILURES_FROM_IP, "1.2.3.4", SecurityAlertStatus.OPEN))
                .thenReturn(Optional.empty());
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findActiveByRoleCode("ADMIN")).thenReturn(List.of(admin));

        int created = detector.detect(now);

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<SecurityAlert> captor = ArgumentCaptor.forClass(SecurityAlert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getSeverity()).isEqualTo(SecurityAlertSeverity.HIGH);
        assertThat(captor.getValue().getEventCount()).isEqualTo(12);
        // Không có transaction trong unit test -> AfterCommit chạy ngay
        verify(notificationService).send(eq("admin@example.test"), contains("1.2.3.4"));
    }

    @Test
    void loginFailuresFromIp_existingOpenAlert_updatesInsteadOfCreating() {
        SecurityAlert open = SecurityAlert.builder().rule(SecurityAlertRule.LOGIN_FAILURES_FROM_IP)
                .severity(SecurityAlertSeverity.HIGH).subject("1.2.3.4").message("old").eventCount(10)
                .firstSeenAt(now.minusSeconds(600)).lastSeenAt(now.minusSeconds(600)).build();
        when(auditLogRepository.countByIpSince(any(), any(), eq(10L))).thenReturn(List.of(ipCount("1.2.3.4", 15)));
        when(alertRepository.findFirstByRuleAndSubjectAndStatus(any(), eq("1.2.3.4"), eq(SecurityAlertStatus.OPEN)))
                .thenReturn(Optional.of(open));

        int created = detector.detect(now);

        assertThat(created).isZero();
        assertThat(open.getEventCount()).isEqualTo(15);
        assertThat(open.getLastSeenAt()).isEqualTo(now);
        verify(alertRepository, never()).save(any());
        verify(notificationService, never()).send(anyString(), anyString());
    }

    @Test
    void tokenReuse_alreadyAlerted_isSkipped() {
        AuditLog log = AuditLog.builder().id(UUID.randomUUID()).action(AuditAction.TOKEN_REUSE_DETECTED)
                .createdAt(now.minusSeconds(60)).build();
        when(auditLogRepository.findByActionInAndCreatedAtAfter(any(), any())).thenReturn(List.of(log));
        when(alertRepository.existsByRuleAndSubject(SecurityAlertRule.REFRESH_TOKEN_REUSE, log.getId().toString()))
                .thenReturn(true);

        assertThat(detector.detect(now)).isZero();
        verify(alertRepository, never()).save(any());
    }

    @Test
    void grantAdmin_createsMediumAlert_withoutMail() {
        UUID targetId = UUID.randomUUID();
        AuditLog log = AuditLog.builder().id(UUID.randomUUID()).action(AuditAction.RBAC_CHANGE)
                .actor(admin).entityId(targetId).metadata(Map.of("detail", "GRANT ADMIN"))
                .createdAt(now.minusSeconds(30)).build();
        when(auditLogRepository.findByActionInAndCreatedAtAfter(any(), any())).thenReturn(List.of(log));
        when(alertRepository.existsByRuleAndSubject(any(), any())).thenReturn(false);
        when(userRepository.findById(targetId))
                .thenReturn(Optional.of(User.builder().id(targetId).email("new-admin@example.test").build()));
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(detector.detect(now)).isEqualTo(1);
        ArgumentCaptor<SecurityAlert> captor = ArgumentCaptor.forClass(SecurityAlert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getRule()).isEqualTo(SecurityAlertRule.ADMIN_ROLE_GRANTED);
        assertThat(captor.getValue().getMessage()).contains("new-admin@example.test");
        verify(notificationService, never()).send(anyString(), anyString());
    }

    @Test
    void rbacChangeOtherThanGrantAdmin_isIgnored() {
        AuditLog log = AuditLog.builder().id(UUID.randomUUID()).action(AuditAction.RBAC_CHANGE)
                .metadata(Map.of("detail", "GRANT LEGAL_VERIFIER")).createdAt(now).build();
        when(auditLogRepository.findByActionInAndCreatedAtAfter(any(), any())).thenReturn(List.of(log));

        assertThat(detector.detect(now)).isZero();
        verify(alertRepository, never()).save(any());
    }
}