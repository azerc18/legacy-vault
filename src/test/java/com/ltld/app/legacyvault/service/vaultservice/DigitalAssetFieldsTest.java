package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetDetailResponse;
import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetRequest;
import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetResponse;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.TransferMechanism;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.AssetAllocationRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DigitalAssetServiceImpl – N3 fields")
class DigitalAssetFieldsTest {

    @Mock private DigitalAssetRepository digitalAssetRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private CryptoService cryptoService;
    @Mock private AuditLogService auditLogService;
    @Mock private VaultModificationGuard vaultModificationGuard;
    @Mock private AssetAllocationRepository assetAllocationRepository;
    @InjectMocks private DigitalAssetServiceImpl service;

    private UUID ownerId;
    private UUID vaultId;
    private Vault vault;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        vaultId = UUID.randomUUID();
        vault = Vault.builder().id(vaultId).status(VaultStatus.ACTIVE)
                .owner(User.builder().id(ownerId).email("o@test.com").build()).build();
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
    }

    private static final String SEED = "a b c d e f g h i j k l";

    private static Map<String, String> wallet() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("walletProvider", "Ledger");
        m.put("blockchainNetwork", "Bitcoin");
        m.put("walletAddress", "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh");
        m.put("seedPhrase", SEED);
        return m;
    }

    @Test
    @DisplayName("tạo ví crypto bằng fields: seed phrase vào secret mã hóa, địa chỉ ở trường công khai, cơ chế SPLIT_KEY")
    void addWithFields() throws Exception {
        when(cryptoService.encrypt(anyString())).thenAnswer(i -> "ENC:" + i.getArgument(0));
        when(digitalAssetRepository.saveAndFlush(any(DigitalAsset.class))).thenAnswer(i -> {
            DigitalAsset a = i.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
        when(assetAllocationRepository.findByAssetId(any())).thenReturn(List.of());

        DigitalAssetRequest req = new DigitalAssetRequest();
        req.setAssetType(AssetType.CRYPTO_WALLET);
        req.setAssetName("Ledger");
        req.setFields(wallet());

        DigitalAssetResponse res = service.addAsset(ownerId, vaultId, req);

        ArgumentCaptor<DigitalAsset> cap = ArgumentCaptor.forClass(DigitalAsset.class);
        verify(digitalAssetRepository).saveAndFlush(cap.capture());
        DigitalAsset saved = cap.getValue();
        assertThat(saved.getTransferMechanism()).isEqualTo(TransferMechanism.CRYPTOGRAPHIC_SPLIT_KEY);
        assertThat(saved.getPublicFieldsJson()).contains("walletAddress").doesNotContain(SEED);
        assertThat(saved.getEncryptedSecret()).startsWith("ENC:").contains("seedPhrase");
        assertThat(res.getPublicFields()).containsKey("walletAddress").doesNotContainKey("seedPhrase");
        assertThat(res.getTransferMechanism()).isEqualTo(TransferMechanism.CRYPTOGRAPHIC_SPLIT_KEY);
    }

    @Test
    @DisplayName("xem chi tiết tài sản kiểu fields: trả đủ trường công khai + bí mật đã giải mã")
    void detailMergesFields() throws Exception {
        UUID assetId = UUID.randomUUID();
        DigitalAsset asset = DigitalAsset.builder().id(assetId).vault(vault).assetType(AssetType.CRYPTO_WALLET)
                .assetName("Ledger").status(AssetStatus.ACTIVE).encryptionKeyRef("v1")
                .publicFieldsJson("{\"walletProvider\":\"Ledger\"}")
                .encryptedSecret("ENC").transferMechanism(TransferMechanism.CRYPTOGRAPHIC_SPLIT_KEY).build();
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.of(asset));
        when(cryptoService.decrypt("ENC")).thenReturn("{\"seedPhrase\":\"alpha beta\"}");

        DigitalAssetDetailResponse d = service.getAsset(ownerId, vaultId, assetId);

        assertThat(d.getFields()).containsEntry("walletProvider", "Ledger").containsEntry("seedPhrase", "alpha beta");
        assertThat(d.getSecret()).isNull();
    }

    @Test
    @DisplayName("M1: tài sản theo schema + secret thô -> ASSET_FIELD_INVALID, dữ liệu không đổi")
    void updateTypedAssetWithRawSecret_rejected() {
        UUID assetId = UUID.randomUUID();
        DigitalAsset asset = DigitalAsset.builder().id(assetId).vault(vault).assetType(AssetType.CRYPTO_WALLET)
                .assetName("Ledger").status(AssetStatus.ACTIVE).encryptionKeyRef("v1")
                .publicFieldsJson("{}").encryptedSecret("OLD").build();
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.of(asset));

        DigitalAssetRequest req = new DigitalAssetRequest();
        req.setAssetType(AssetType.CRYPTO_WALLET);
        req.setAssetName("Ledger");
        req.setSecret("abc");

        assertThatThrownBy(() -> service.updateAsset(ownerId, vaultId, assetId, req))
                .isInstanceOf(VaultException.class)
                .extracting(e -> ((VaultException) e).getErrorCode()).isEqualTo(ErrorCode.ASSET_FIELD_INVALID);
        assertThat(asset.getEncryptedSecret()).isEqualTo("OLD");
    }

    @Test
    @DisplayName("M1: tài sản theo schema đổi loại mà không có fields -> ASSET_FIELD_INVALID")
    void updateTypedAssetChangeTypeWithoutFields_rejected() {
        UUID assetId = UUID.randomUUID();
        DigitalAsset asset = DigitalAsset.builder().id(assetId).vault(vault).assetType(AssetType.CRYPTO_WALLET)
                .assetName("Ledger").status(AssetStatus.ACTIVE).encryptionKeyRef("v1")
                .publicFieldsJson("{}").encryptedSecret("OLD").build();
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.of(asset));

        DigitalAssetRequest req = new DigitalAssetRequest();
        req.setAssetType(AssetType.DOCUMENT);
        req.setAssetName("Ledger");

        assertThatThrownBy(() -> service.updateAsset(ownerId, vaultId, assetId, req))
                .isInstanceOf(VaultException.class);
        assertThat(asset.getAssetType()).isEqualTo(AssetType.CRYPTO_WALLET);
    }

    @Test
    @DisplayName("m4: danh sách che số tài khoản, còn 4 số cuối")
    void listMasksAccountNumber() {
        UUID assetId = UUID.randomUUID();
        DigitalAsset asset = DigitalAsset.builder().id(assetId).vault(vault).assetType(AssetType.BANK_ACCOUNT)
                .assetName("VCB").status(AssetStatus.ACTIVE).encryptionKeyRef("v1")
                .publicFieldsJson("{\"institutionName\":\"Vietcombank\",\"accountNumber\":\"0123456789\"}")
                .encryptedSecret("ENC").build();
        when(digitalAssetRepository.findByVaultIdAndStatus(vaultId, AssetStatus.ACTIVE)).thenReturn(List.of(asset));
        when(assetAllocationRepository.findByAssetIdIn(any())).thenReturn(List.of());

        List<DigitalAssetResponse> list = service.listAssets(ownerId, vaultId);

        assertThat(list.get(0).getPublicFields())
                .containsEntry("accountNumber", "****6789").containsEntry("institutionName", "Vietcombank");
    }

    @Test
    @DisplayName("m1: ví crypto không có seed phrase lẫn private key -> ASSET_FIELD_INVALID")
    void addWalletWithoutSecret_rejected() {
        Map<String, String> f = wallet();
        f.remove("seedPhrase");
        DigitalAssetRequest req = new DigitalAssetRequest();
        req.setAssetType(AssetType.CRYPTO_WALLET);
        req.setAssetName("Ledger");
        req.setFields(f);

        assertThatThrownBy(() -> service.addAsset(ownerId, vaultId, req)).isInstanceOf(VaultException.class);
    }

    @Test
    @DisplayName("sửa tài sản: không gửi lại seed phrase thì giữ giá trị cũ")
    void updateKeepsOldSecret() throws Exception {
        UUID assetId = UUID.randomUUID();
        DigitalAsset asset = DigitalAsset.builder().id(assetId).vault(vault).assetType(AssetType.CRYPTO_WALLET)
                .assetName("Ledger").status(AssetStatus.ACTIVE).encryptionKeyRef("v1")
                .publicFieldsJson("{}").encryptedSecret("OLD").build();
        when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.of(asset));
        when(cryptoService.decrypt("OLD")).thenReturn("{\"seedPhrase\":\"" + SEED + "\"}");
        when(cryptoService.encrypt(anyString())).thenAnswer(i -> "ENC:" + i.getArgument(0));
        when(digitalAssetRepository.saveAndFlush(any(DigitalAsset.class))).thenAnswer(i -> i.getArgument(0));
        when(assetAllocationRepository.findByAssetId(any())).thenReturn(List.of());

        Map<String, String> f = wallet();
        f.remove("seedPhrase");
        DigitalAssetRequest req = new DigitalAssetRequest();
        req.setAssetType(AssetType.CRYPTO_WALLET);
        req.setAssetName("Ledger 2");
        req.setFields(f);

        service.updateAsset(ownerId, vaultId, assetId, req);

        assertThat(asset.getEncryptedSecret()).contains("seedPhrase").contains(SEED);
        assertThat(asset.getAssetName()).isEqualTo("Ledger 2");
    }
}
