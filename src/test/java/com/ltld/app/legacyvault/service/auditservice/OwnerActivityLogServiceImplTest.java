package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.auditdto.VaultActivityLogResponse;
import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
import com.ltld.app.legacyvault.service.dmsservice.DmsActivationProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerActivityLogServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Spy
    private AuditDescriptionBuilder descriptionBuilder = new AuditDescriptionBuilder();

    @InjectMocks
    private OwnerActivityLogServiceImpl ownerActivityLogService;

    private final UUID ownerId = UUID.randomUUID();
    private final Pageable pageable = PageRequest.of(1, 5);

    @Test
    void getOwnerActivityLogs_queriesByOwnerAndDmsExecutorEntityTypes() {
        when(auditLogRepository.findOwnerActivity(any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty(pageable));
        Instant from = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant to = Instant.now();

        ownerActivityLogService.getOwnerActivityLogs(ownerId, from, to, pageable);

        verify(auditLogRepository).findOwnerActivity(eq(ownerId),
                eq(DmsActivationProcessor.ENTITY_DMS_CONFIG), eq(DmsActivationProcessor.ENTITY_EXECUTOR_ASSIGNMENT),
                eq(from), eq(to), eq(pageable));
    }

    @Test
    void getOwnerActivityLogs_noDates_defaultsToLast30Days() {
        when(auditLogRepository.findOwnerActivity(any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty(pageable));
        Instant before = Instant.now();

        ownerActivityLogService.getOwnerActivityLogs(ownerId, null, null, pageable);

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(auditLogRepository).findOwnerActivity(eq(ownerId), any(), any(),
                fromCaptor.capture(), toCaptor.capture(), eq(pageable));
        assertThat(toCaptor.getValue()).isAfterOrEqualTo(before);
        assertThat(Duration.between(fromCaptor.getValue(), toCaptor.getValue())).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void getOwnerActivityLogs_startDateAfterEndDate_throwsBadRequest() {
        Instant now = Instant.now();

        assertThatThrownBy(() -> ownerActivityLogService.getOwnerActivityLogs(
                ownerId, now, now.minus(5, ChronoUnit.DAYS), pageable))
                .isInstanceOf(DmsException.class)
                .satisfies(ex -> assertThat(((DmsException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void getOwnerActivityLogs_rangeExceedsOneYear_throwsBadRequest() {
        Instant to = Instant.now();

        assertThatThrownBy(() -> ownerActivityLogService.getOwnerActivityLogs(
                ownerId, to.minus(400, ChronoUnit.DAYS), to, pageable))
                .isInstanceOf(DmsException.class)
                .satisfies(ex -> assertThat(((DmsException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void getOwnerActivityLogs_ownerWithoutDmsOrExecutor_returnsEmptyPage() {
        when(auditLogRepository.findOwnerActivity(any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty(pageable));

        Page<VaultActivityLogResponse> result = ownerActivityLogService.getOwnerActivityLogs(
                ownerId, null, null, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void getOwnerActivityLogs_mapsLogsAndKeepsPaging() {
        User owner = new User();
        owner.setId(ownerId);
        AuditLog checkin = AuditLog.builder()
                .id(UUID.randomUUID())
                .actor(owner)
                .action(AuditAction.DMS_CHECKIN)
                .ipAddress("192.168.1.100")
                .createdAt(Instant.now())
                .build();
        AuditLog triggered = AuditLog.builder()
                .id(UUID.randomUUID())
                .actor(null)
                .action(AuditAction.DMS_TRIGGERED)
                .createdAt(Instant.now())
                .build();
        when(auditLogRepository.findOwnerActivity(any(), any(), any(), any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(checkin, triggered), pageable, 12));

        Page<VaultActivityLogResponse> result = ownerActivityLogService.getOwnerActivityLogs(
                ownerId, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(12);
        assertThat(result.getPageable()).isEqualTo(pageable);
        assertThat(result.getContent()).extracting(VaultActivityLogResponse::getActorName)
                .containsExactly("You", "System");
        assertThat(result.getContent()).extracting(VaultActivityLogResponse::getDescription)
                .containsExactly("Checked in to confirm still active.",
                        "The Dead Man's Switch was triggered because the check-in deadline passed.");
        assertThat(result.getContent().get(0).getIpAddress()).isEqualTo("192.168.1.100");
    }
}
