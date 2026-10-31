package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.auditdto.VaultActivityLogResponse;
import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerActivityLogServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditDescriptionBuilder descriptionBuilder;

    @InjectMocks
    private OwnerActivityLogServiceImpl ownerActivityLogService;

    @Test
    void getEstateAccessLogs_returnsMappedAccessEvents() {
        UUID ownerId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        AuditLog log = AuditLog.builder()
                .id(UUID.randomUUID())
                .action(AuditAction.ASSET_SECRET_REVEALED)
                .entityType("DigitalAsset")
                .entityId(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();

        VaultActivityLogResponse expected = VaultActivityLogResponse.builder()
                .id(log.getId())
                .action(AuditAction.ASSET_SECRET_REVEALED)
                .description("Secret of asset \"Seed Phrase\" was revealed.")
                .build();

        when(auditLogRepository.findEstateAccessActivity(
                eq(ownerId),
                eq(AuditDescriptionBuilder.ESTATE_ACCESS_ACTIONS),
                any(Instant.class),
                any(Instant.class),
                eq(pageable)
        )).thenReturn(new PageImpl<>(List.of(log)));

        when(descriptionBuilder.toResponse(log, ownerId)).thenReturn(expected);

        Page<VaultActivityLogResponse> result = ownerActivityLogService.getEstateAccessLogs(ownerId, null, null, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(AuditAction.ASSET_SECRET_REVEALED, result.getContent().get(0).getAction());
        assertTrue(AuditDescriptionBuilder.ESTATE_ACCESS_ACTIONS.contains(AuditAction.ADMIN_VAULT_VIEWED));
        assertTrue(AuditDescriptionBuilder.ESTATE_ACCESS_ACTIONS.contains(AuditAction.EXECUTOR_VAULT_VIEWED));
        assertTrue(AuditDescriptionBuilder.ESTATE_ACCESS_ACTIONS.contains(AuditAction.EMERGENCY_ACCESS_GRANTED));
        assertTrue(AuditDescriptionBuilder.ESTATE_ACCESS_ACTIONS.contains(AuditAction.ASSET_ACCESSED));
        assertTrue(AuditDescriptionBuilder.ESTATE_ACCESS_ACTIONS.contains(AuditAction.ASSET_SECRET_REVEALED));
        verify(auditLogRepository).findEstateAccessActivity(
                eq(ownerId),
                eq(AuditDescriptionBuilder.ESTATE_ACCESS_ACTIONS),
                any(Instant.class),
                any(Instant.class),
                eq(pageable)
        );
    }

    @Test
    void getEstateAccessLogs_whenStartAfterEnd_throwsBadRequest() {
        UUID ownerId = UUID.randomUUID();
        Instant start = Instant.parse("2026-10-31T10:00:00Z");
        Instant end = Instant.parse("2026-10-01T10:00:00Z");

        assertThrows(DmsException.class, () ->
                ownerActivityLogService.getEstateAccessLogs(ownerId, start, end, PageRequest.of(0, 20)));
    }
}
