package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetDetailResponse;
import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetRequest;
import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetResponse;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DigitalAssetServiceImpl – V1/V2/V4")
class DigitalAssetServiceImplTest {

    @Mock private DigitalAssetRepository digitalAssetRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private CryptoService cryptoService;
    @Mock private AuditLogService auditLogService;
    @Mock private VaultModificationGuard vaultModificationGuard;

    @InjectMocks private DigitalAssetServiceImpl assetService;

    private UUID ownerId;
    private UUID otherOwnerId;
    private UUID vaultId;
    private UUID assetId;
    private User mockOwner;
    private Vault mockVault;
    private DigitalAsset mockAsset;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        otherOwnerId = UUID.randomUUID();
        vaultId = UUID.randomUUID();
        assetId = UUID.randomUUID();

        mockOwner = User.builder().id(ownerId).email("owner@test.com").build();

        mockVault = Vault.builder()
                .id(vaultId)
                .owner(mockOwner)
                .status(VaultStatus.ACTIVE)
                .build();

        mockAsset = DigitalAsset.builder()
                .id(assetId)
                .vault(mockVault)
                .assetType(AssetType.BANK_ACCOUNT)
                .assetName("MB Bank")
                .encryptedSecret("ENC:abc123")
                .encryptionKeyRef("v1")
                .notesEncrypted("ENC:notes")
                .status(AssetStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ===== V1: listAssets =====

    @Test
    @DisplayName("V1: Owner xem danh sách tài sản → trả list, không có secret")
    void listAssets_ownerAndActiveAssets_returnsList() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(digitalAssetRepository.findByVaultIdAndStatus(vaultId, AssetStatus.ACTIVE))
                .thenReturn(List.of(mockAsset));

        List<DigitalAssetResponse> result = assetService.listAssets(ownerId, vaultId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(assetId);
        assertThat(result.get(0).getAssetName()).isEqualTo("MB Bank");
    }

    @Test
    @DisplayName("V1: Owner A xem vault của Owner B → 404")
    void listAssets_wrongOwner_throws404() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));

        assertThatThrownBy(() -> assetService.listAssets(otherOwnerId, vaultId))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ===== V2: getAsset =====

    @Test
    @DisplayName("V2: Owner xem chi tiết asset → trả secret đã giải mã")
    void getAsset_validOwner_returnsDecryptedSecret() throws Exception {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.of(mockAsset));
        when(cryptoService.decrypt("ENC:abc123")).thenReturn("my-plain-secret");
        when(cryptoService.decrypt("ENC:notes")).thenReturn("plain notes");

        DigitalAssetDetailResponse detail = assetService.getAsset(ownerId, vaultId, assetId);

        assertThat(detail.getSecret()).isEqualTo("my-plain-secret");
        assertThat(detail.getNotes()).isEqualTo("plain notes");
        verify(auditLogService).log(any(), any(), eq(ownerId), any(), eq(vaultId), any(), any(), any());
    }

    @Test
    @DisplayName("V2: Asset không thuộc vault → 404")
    void getAsset_assetNotInVault_throws404() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetService.getAsset(ownerId, vaultId, assetId))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ===== V4a: addAsset =====

    @Test
    @DisplayName("V4a: Thêm asset hợp lệ → lưu DB, trả response, ghi audit")
    void addAsset_validRequest_savesAndReturns() throws Exception {
        DigitalAssetRequest req = new DigitalAssetRequest();
        req.setAssetType(AssetType.CRYPTO_WALLET);
        req.setAssetName("BTC Wallet");
        req.setSecret("my-seed-phrase");
        req.setNotes("cold wallet");

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(cryptoService.encrypt("my-seed-phrase")).thenReturn("ENC:seed");
        when(cryptoService.encrypt("cold wallet")).thenReturn("ENC:notes");
        when(digitalAssetRepository.save(any())).thenAnswer(inv -> {
            DigitalAsset a = inv.getArgument(0);
            a = DigitalAsset.builder()
                    .id(UUID.randomUUID())
                    .vault(mockVault)
                    .assetType(a.getAssetType())
                    .assetName(a.getAssetName())
                    .encryptedSecret(a.getEncryptedSecret())
                    .encryptionKeyRef(a.getEncryptionKeyRef())
                    .status(AssetStatus.ACTIVE)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            return a;
        });

        DigitalAssetResponse resp = assetService.addAsset(ownerId, vaultId, req);

        assertThat(resp.getAssetName()).isEqualTo("BTC Wallet");
        assertThat(resp.getAssetType()).isEqualTo(AssetType.CRYPTO_WALLET);
        verify(auditLogService).log(any(), any(), eq(ownerId), any(), eq(vaultId), any(), any(), any());
    }

    @Test
    @DisplayName("V4a: Secret rỗng khi thêm mới → BAD_REQUEST 400")
    void addAsset_blankSecret_throwsBadRequest() {
        DigitalAssetRequest req = new DigitalAssetRequest();
        req.setAssetType(AssetType.BANK_ACCOUNT);
        req.setAssetName("MB Bank");
        req.setSecret("");

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));

        assertThatThrownBy(() -> assetService.addAsset(ownerId, vaultId, req))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);

        verify(digitalAssetRepository, never()).save(any());
    }

    @Test
    @DisplayName("V4a: DMS đã TRIGGERED → Guard chặn thêm asset → CONFLICT 409")
    void addAsset_dmsTriggered_throwsConflict() {
        DigitalAssetRequest req = new DigitalAssetRequest();
        req.setAssetType(AssetType.BANK_ACCOUNT);
        req.setAssetName("VCB");
        req.setSecret("1234");

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        doThrow(new VaultException("DMS triggered", HttpStatus.CONFLICT))
                .when(vaultModificationGuard).ensureModifiable(mockVault);

        assertThatThrownBy(() -> assetService.addAsset(ownerId, vaultId, req))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);

        verify(digitalAssetRepository, never()).save(any());
    }

    // ===== V4b: updateAsset =====

    @Test
    @DisplayName("V4b: Cập nhật asset hợp lệ (có secret mới) → lưu và trả response")
    void updateAsset_withNewSecret_updatesAndReturns() throws Exception {
        DigitalAssetRequest req = new DigitalAssetRequest();
        req.setAssetType(AssetType.BANK_ACCOUNT);
        req.setAssetName("MB Bank Updated");
        req.setSecret("new-password");
        req.setNotes(null);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.of(mockAsset));
        when(cryptoService.encrypt("new-password")).thenReturn("ENC:new");
        when(digitalAssetRepository.save(any())).thenReturn(mockAsset);

        DigitalAssetResponse resp = assetService.updateAsset(ownerId, vaultId, assetId, req);

        assertThat(resp).isNotNull();
        verify(cryptoService).encrypt("new-password");
        verify(auditLogService).log(any(), any(), eq(ownerId), any(), eq(vaultId), any(), any(), any());
    }

    @Test
    @DisplayName("V4b: Cập nhật asset không gửi secret → giữ nguyên secret cũ")
    void updateAsset_nullSecret_keepsOldSecret() throws Exception {
        DigitalAssetRequest req = new DigitalAssetRequest();
        req.setAssetType(AssetType.BANK_ACCOUNT);
        req.setAssetName("MB Bank Renamed");
        req.setSecret(null); // không gửi secret mới

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.of(mockAsset));
        when(digitalAssetRepository.save(any())).thenReturn(mockAsset);

        assetService.updateAsset(ownerId, vaultId, assetId, req);

        // cryptoService.encrypt KHÔNG được gọi (secret cũ giữ nguyên)
        verify(cryptoService, never()).encrypt(any());
    }

    // ===== V4c: deleteAsset =====

    @Test
    @DisplayName("V4c: Xóa asset hợp lệ → ghi audit rồi hard-delete")
    void deleteAsset_validOwner_deletesFromDb() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.of(mockAsset));

        assetService.deleteAsset(ownerId, vaultId, assetId);

        verify(auditLogService).log(any(), any(), eq(ownerId), any(), eq(vaultId), any(), eq(assetId.toString()), any());
        verify(digitalAssetRepository).delete(mockAsset);
    }

    @Test
    @DisplayName("V4c: DMS TRIGGERED → Guard chặn xóa asset")
    void deleteAsset_dmsTriggered_throwsConflict() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        doThrow(new VaultException("DMS triggered", HttpStatus.CONFLICT))
                .when(vaultModificationGuard).ensureModifiable(mockVault);

        assertThatThrownBy(() -> assetService.deleteAsset(ownerId, vaultId, assetId))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);

        verify(digitalAssetRepository, never()).delete(any());
    }
}
