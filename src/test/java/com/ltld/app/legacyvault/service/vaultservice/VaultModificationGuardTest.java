package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VaultModificationGuardTest {

    @Mock
    private DmsConfigRepository dmsConfigRepository;

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

    @Test
    @DisplayName("Vault không ở trạng thái ACTIVE -> Ném BAD_REQUEST 400")
    void ensureModifiable_vaultNotActive_throwsBadRequest() {
        vault.setStatus(VaultStatus.UNLOCKED);

        VaultException ex = assertThrows(VaultException.class, () -> guard.ensureModifiable(vault));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Cannot modify vault that is not in active state", ex.getMessage());
    }

    @Test
    @DisplayName("DMS của Owner đã TRIGGERED -> Ném CONFLICT 409")
    void ensureModifiable_dmsTriggered_throwsConflict() {
        DmsConfig dms = DmsConfig.builder().status(DmsStatus.TRIGGERED).build();
        when(dmsConfigRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(dms));

        VaultException ex = assertThrows(VaultException.class, () -> guard.ensureModifiable(vault));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Cannot modify vault: dead-man switch has been triggered", ex.getMessage());
    }

    @Test
    @DisplayName("Két đang có hồ sơ xác minh mở (PENDING/APPROVED) -> Ném CONFLICT 409")
    void ensureModifiable_openVerificationExists_throwsConflict() {
        when(dmsConfigRepository.findByOwnerId(ownerId)).thenReturn(Optional.empty());
        when(verificationRequestVaultRepository.findVaultIdsWithOpenRequest(anyList(), anyList()))
                .thenReturn(List.of(vaultId));

        VaultException ex = assertThrows(VaultException.class, () -> guard.ensureModifiable(vault));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Cannot modify vault while a verification request is pending or approved", ex.getMessage());
    }

    @Test
    @DisplayName("Két ACTIVE, DMS không TRIGGERED, không có hồ sơ mở -> Cho phép sửa đổi")
    void ensureModifiable_valid_doesNotThrow() {
        DmsConfig dms = DmsConfig.builder().status(DmsStatus.ACTIVE).build();
        when(dmsConfigRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(dms));
        when(verificationRequestVaultRepository.findVaultIdsWithOpenRequest(anyList(), anyList()))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> guard.ensureModifiable(vault));
    }
}
