package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.dto.assetverificationdto.AssetVerificationResponse;
import com.ltld.app.legacyvault.entity.AssetVerification;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.AssetVerificationStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.AssetVerificationRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssetVerificationServiceImpl – C.5 khung (getStatus)")
class AssetVerificationServiceImplTest {

    @Mock private AssetVerificationRepository verificationRepository;
    @Mock private DigitalAssetRepository digitalAssetRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private ObjectProvider<AssetVerifier> verifiers;

    @InjectMocks private AssetVerificationServiceImpl service;

    private UUID ownerId;
    private UUID otherOwnerId;
    private UUID vaultId;
    private UUID assetId;
    private Vault mockVault;
    private DigitalAsset mockAsset;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        otherOwnerId = UUID.randomUUID();
        vaultId = UUID.randomUUID();
        assetId = UUID.randomUUID();

        User owner = User.builder().id(ownerId).email("owner@test.com").build();
        mockVault = Vault.builder().id(vaultId).owner(owner).status(VaultStatus.ACTIVE).build();
        mockAsset = DigitalAsset.builder()
                .id(assetId)
                .vault(mockVault)
                .assetType(AssetType.BANK_ACCOUNT)
                .assetName("MB Bank")
                .status(AssetStatus.ACTIVE)
                .build();
    }

    // ===== getStatus: trạng thái =====

    @Test
    @DisplayName("Chưa có dòng xác thực nào → SELF_DECLARED, method/provider null")
    void getStatus_noRow_returnsSelfDeclared() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(digitalAssetRepository.findById(assetId)).thenReturn(Optional.of(mockAsset));
        when(verifiers.orderedStream()).thenReturn(Stream.empty());
        when(verificationRepository.findFirstByAssetIdOrderByCreatedAtDesc(assetId))
                .thenReturn(Optional.empty());

        AssetVerificationResponse result = service.getStatus(ownerId, vaultId, assetId);

        assertThat(result.getAssetId()).isEqualTo(assetId);
        assertThat(result.getStatus()).isEqualTo(AssetVerificationStatus.SELF_DECLARED);
        assertThat(result.getMethod()).isNull();
        assertThat(result.getProvider()).isNull();
        assertThat(result.getAvailableMethods()).isEmpty();
    }

    @Test
    @DisplayName("Có dòng xác thực → trả đúng dữ liệu của dòng mới nhất do repository trả về")
    void getStatus_hasRow_returnsLatestRow() {
        LocalDateTime verifiedAt = LocalDateTime.now().minusDays(1);
        LocalDateTime expiresAt = LocalDateTime.now().plusMonths(6);
        AssetVerification latest = AssetVerification.builder()
                .id(UUID.randomUUID())
                .asset(mockAsset)
                .method(AssetVerificationMethod.BANK_FORMAT_CHECK)
                .provider(VerificationProvider.SANDBOX)
                .status(AssetVerificationStatus.VERIFIED_SANDBOX)
                .verifiedAt(verifiedAt)
                .expiresAt(expiresAt)
                .build();

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(digitalAssetRepository.findById(assetId)).thenReturn(Optional.of(mockAsset));
        when(verifiers.orderedStream()).thenReturn(Stream.empty());
        when(verificationRepository.findFirstByAssetIdOrderByCreatedAtDesc(assetId))
                .thenReturn(Optional.of(latest));

        AssetVerificationResponse result = service.getStatus(ownerId, vaultId, assetId);

        assertThat(result.getStatus()).isEqualTo(AssetVerificationStatus.VERIFIED_SANDBOX);
        assertThat(result.getMethod()).isEqualTo(AssetVerificationMethod.BANK_FORMAT_CHECK);
        assertThat(result.getProvider()).isEqualTo(VerificationProvider.SANDBOX);
        assertThat(result.getVerifiedAt()).isEqualTo(verifiedAt);
        assertThat(result.getExpiresAt()).isEqualTo(expiresAt);
        verify(verificationRepository).findFirstByAssetIdOrderByCreatedAtDesc(assetId);
    }

    @Test
    @DisplayName("availableMethods chỉ lấy từ verifier đúng loại tài sản")
    void getStatus_availableMethods_onlyFromMatchingVerifier() {
        AssetVerifier bankVerifier = mock(AssetVerifier.class);
        when(bankVerifier.type()).thenReturn(AssetType.BANK_ACCOUNT);
        when(bankVerifier.methods()).thenReturn(List.of(
                AssetVerificationMethod.BANK_FORMAT_CHECK, AssetVerificationMethod.STATEMENT_UPLOAD));

        AssetVerifier cryptoVerifier = mock(AssetVerifier.class);
        when(cryptoVerifier.type()).thenReturn(AssetType.CRYPTO_WALLET);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(digitalAssetRepository.findById(assetId)).thenReturn(Optional.of(mockAsset));
        when(verifiers.orderedStream()).thenReturn(Stream.of(bankVerifier, cryptoVerifier));
        when(verificationRepository.findFirstByAssetIdOrderByCreatedAtDesc(assetId))
                .thenReturn(Optional.empty());

        AssetVerificationResponse result = service.getStatus(ownerId, vaultId, assetId);

        assertThat(result.getAvailableMethods())
                .containsExactly(AssetVerificationMethod.BANK_FORMAT_CHECK,
                        AssetVerificationMethod.STATEMENT_UPLOAD);
        verify(cryptoVerifier, never()).methods();
    }

    // ===== getStatus: quyền truy cập → 404 =====

    @Test
    @DisplayName("Owner khác xem trạng thái → 404, không đụng tới repository xác thực")
    void getStatus_wrongOwner_throws404() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));

        assertThatThrownBy(() -> service.getStatus(otherOwnerId, vaultId, assetId))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(verificationRepository);
    }

    @Test
    @DisplayName("Tài sản thuộc vault khác → 404")
    void getStatus_assetInAnotherVault_throws404() {
        Vault otherVault = Vault.builder()
                .id(UUID.randomUUID()).owner(mockVault.getOwner()).status(VaultStatus.ACTIVE).build();
        DigitalAsset assetOfOtherVault = DigitalAsset.builder()
                .id(assetId).vault(otherVault).assetType(AssetType.BANK_ACCOUNT)
                .status(AssetStatus.ACTIVE).build();

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(digitalAssetRepository.findById(assetId)).thenReturn(Optional.of(assetOfOtherVault));

        assertThatThrownBy(() -> service.getStatus(ownerId, vaultId, assetId))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(verificationRepository);
    }

    @Test
    @DisplayName("Tài sản đã REVOKED → 404")
    void getStatus_revokedAsset_throws404() {
        mockAsset.setStatus(AssetStatus.REVOKED);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(digitalAssetRepository.findById(assetId)).thenReturn(Optional.of(mockAsset));

        assertThatThrownBy(() -> service.getStatus(ownerId, vaultId, assetId))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(verificationRepository);
    }
}
