package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VaultModificationGuardTest {

    @Mock
    private DmsConfigService dmsConfigService;

    @Mock
    private VerificationRequestVaultRepository verificationRequestVaultRepository;

    @InjectMocks
    private VaultModificationGuard guard;

    private User owner;
    private Vault vault;
    private UUID ownerId;
    private UUID vaultId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        vaultId = UUID.randomUUID();

        owner = User.builder().id(ownerId).email("owner@test.com").build();
        vault = Vault.builder()
                .id(vaultId)
                .owner(owner)
                .status(VaultStatus.ACTIVE)
                .build();
    }

    // ── ensureModifiable ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Vault không ở trạng thái ACTIVE -> Ném BAD_REQUEST 400")
    void ensureModifiable_vaultNotActive_throwsBadRequest() {
        vault.setStatus(VaultStatus.UNLOCKED);

        VaultException ex = assertThrows(VaultException.class, () -> guard.ensureModifiable(vault));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Cannot modify vault that is not in active state", ex.getMessage());
        verifyNoInteractions(dmsConfigService);
    }

    @Test
    @DisplayName("DMS của Owner đã TRIGGERED -> dmsConfigService ném CONFLICT 409")
    void ensureModifiable_dmsTriggered_throwsConflict() {
        doThrow(new DmsException("The Dead Man's Switch has started the handover process and cannot be changed.", HttpStatus.CONFLICT))
                .when(dmsConfigService).assertHandoverNotStarted(ownerId);

        DmsException ex = assertThrows(DmsException.class, () -> guard.ensureModifiable(vault));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(dmsConfigService).assertHandoverNotStarted(ownerId);
    }

    @Test
    @DisplayName("Két đang có hồ sơ xác minh mở (PENDING/APPROVED) -> Ném CONFLICT 409")
    void ensureModifiable_openVerificationExists_throwsConflict() {
        when(verificationRequestVaultRepository.findVaultIdsWithOpenRequest(anyList(), anyList()))
                .thenReturn(List.of(vaultId));

        VaultException ex = assertThrows(VaultException.class, () -> guard.ensureModifiable(vault));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Cannot modify vault while a verification request is pending or approved", ex.getMessage());
        verify(dmsConfigService).assertHandoverNotStarted(ownerId);
    }

    @Test
    @DisplayName("Két ACTIVE, DMS không TRIGGERED, không có hồ sơ mở -> Cho phép sửa đổi")
    void ensureModifiable_valid_doesNotThrow() {
        when(verificationRequestVaultRepository.findVaultIdsWithOpenRequest(anyList(), anyList()))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> guard.ensureModifiable(vault));
        verify(dmsConfigService).assertHandoverNotStarted(ownerId);
    }

    // ── lockReason (C1 fix: isHandoverStarted thay cho assertHandoverNotStarted) ────────────
    // lockReason() được gọi từ @Transactional(readOnly=true) (getMyVaults, getVaultDetail).
    // assertHandoverNotStarted dùng PESSIMISTIC_FORCE_INCREMENT -> rollback-only -> HTTP 500.
    // isHandoverStarted chỉ đọc, không khóa, an toàn trong read-only transaction.

    @Test
    @DisplayName("lockReason: Vault không ACTIVE -> trả về lý do, không gọi DMS service")
    void lockReason_vaultNotActive_returnsReasonWithoutDmsCall() {
        vault.setStatus(VaultStatus.UNLOCKED);

        Optional<String> reason = guard.lockReason(vault);

        assertTrue(reason.isPresent());
        assertEquals("This vault is no longer active.", reason.get());
        verifyNoInteractions(dmsConfigService);
    }

    @Test
    @DisplayName("lockReason: DMS TRIGGERED -> gọi isHandoverStarted (không ném exception), trả về lý do khóa")
    void lockReason_dmsTriggered_usesIsHandoverStarted_returnsReason() {
        when(dmsConfigService.isHandoverStarted(ownerId)).thenReturn(true);

        Optional<String> reason = guard.lockReason(vault);

        assertTrue(reason.isPresent());
        assertEquals("The handover process has started.", reason.get());
        // Phải gọi isHandoverStarted, KHÔNG được gọi assertHandoverNotStarted (C1)
        verify(dmsConfigService).isHandoverStarted(ownerId);
        verify(dmsConfigService, never()).assertHandoverNotStarted(any());
    }

    @Test
    @DisplayName("lockReason: DMS không TRIGGERED, có hồ sơ xác minh mở -> trả về lý do verification")
    void lockReason_openVerification_returnsVerificationReason() {
        when(dmsConfigService.isHandoverStarted(ownerId)).thenReturn(false);
        when(verificationRequestVaultRepository.findVaultIdsWithOpenRequest(anyList(), anyList()))
                .thenReturn(List.of(vaultId));

        Optional<String> reason = guard.lockReason(vault);

        assertTrue(reason.isPresent());
        assertEquals("A verification request is pending or approved", reason.get());
        verify(dmsConfigService).isHandoverStarted(ownerId);
        verify(dmsConfigService, never()).assertHandoverNotStarted(any());
    }

    @Test
    @DisplayName("lockReason: Vault ACTIVE, DMS không TRIGGERED, không có hồ sơ mở -> empty (có thể sửa)")
    void lockReason_fullyModifiable_returnsEmpty() {
        when(dmsConfigService.isHandoverStarted(ownerId)).thenReturn(false);
        when(verificationRequestVaultRepository.findVaultIdsWithOpenRequest(anyList(), anyList()))
                .thenReturn(List.of());

        Optional<String> reason = guard.lockReason(vault);

        assertTrue(reason.isEmpty());
        verify(dmsConfigService).isHandoverStarted(ownerId);
        verify(dmsConfigService, never()).assertHandoverNotStarted(any());
    }
}
