package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetRequest;
import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetResponse;
import com.ltld.app.legacyvault.entity.AssetAllocation;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.AllocationMode;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.enums.TransferMechanism;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.AssetAllocationRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.paymentservice.EntitlementService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DigitalAssetServiceImpl – N2-owner: phân bổ tài sản")
class AssetAllocationServiceTest {

    @Mock private DigitalAssetRepository digitalAssetRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private CryptoService cryptoService;
    @Mock private AuditLogService auditLogService;
    @Mock private VaultModificationGuard vaultModificationGuard;
    @Mock private AssetAllocationRepository assetAllocationRepository;
    @Mock private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Mock private EntitlementService entitlementService;

    @InjectMocks private DigitalAssetServiceImpl assetService;

    private UUID ownerId;
    private UUID vaultId;
    private UUID assetId;
    private Vault vault;
    private DigitalAsset asset;
    private VaultBeneficiary bA;
    private VaultBeneficiary bB;
    private VaultBeneficiary bC;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        vaultId = UUID.randomUUID();
        assetId = UUID.randomUUID();

        User owner = User.builder().id(ownerId).email("owner@test.com").build();
        vault = Vault.builder().id(vaultId).owner(owner).status(VaultStatus.ACTIVE).build();
        asset = DigitalAsset.builder()
                .id(assetId)
                .vault(vault)
                .assetType(AssetType.BANK_ACCOUNT)
                .assetName("MB Bank")
                .encryptedSecret("ENC:abc")
                .encryptionKeyRef("v1")
                .status(AssetStatus.ACTIVE)
                .build();

        bA = beneficiary("a@test.com");
        bB = beneficiary("b@test.com");
        bC = beneficiary("c@test.com");

        lenient().when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        lenient().when(digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE))
                .thenReturn(Optional.of(asset));
        lenient().when(digitalAssetRepository.saveAndFlush(any(DigitalAsset.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId))
                .thenReturn(List.of(bA, bB, bC));
    }

    // ===== helpers =====

    private VaultBeneficiary beneficiary(String email) {
        return VaultBeneficiary.builder()
                .id(UUID.randomUUID())
                .vault(vault)
                .email(email)
                .status(BeneficiaryInviteStatus.INVITED)
                .build();
    }

    private static DigitalAssetRequest.Recipient recipient(VaultBeneficiary b, String percent,
                                                           VaultBeneficiary contingent) {
        DigitalAssetRequest.Recipient r = new DigitalAssetRequest.Recipient();
        r.setBeneficiaryId(b.getId());
        r.setSharePercent(percent == null ? null : new BigDecimal(percent));
        r.setContingentBeneficiaryId(contingent == null ? null : contingent.getId());
        return r;
    }

    private static DigitalAssetRequest.AllocationRequest alloc(AllocationMode mode,
                                                               DigitalAssetRequest.Recipient... recipients) {
        DigitalAssetRequest.AllocationRequest a = new DigitalAssetRequest.AllocationRequest();
        a.setMode(mode);
        a.setRecipients(List.of(recipients));
        return a;
    }

    private DigitalAssetResponse update(AssetType type, DigitalAssetRequest.AllocationRequest allocation)
            throws Exception {
        DigitalAssetRequest req = new DigitalAssetRequest();
        req.setAssetType(type);
        req.setAssetName("MB Bank");
        req.setAllocation(allocation);
        return assetService.updateAsset(ownerId, vaultId, assetId, req);
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(VaultException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private static void assertStatus(ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call).isInstanceOfSatisfying(VaultException.class,
                e -> assertThat(e.getStatus()).isEqualTo(status));
    }

    // ===== PROPORTIONAL =====

    @Test
    @DisplayName("PROPORTIONAL: tổng ≠ 100% → ALLOCATION_PERCENT_INVALID, không ghi gì")
    void proportional_sumNot100_throws() {
        var a = alloc(AllocationMode.PROPORTIONAL, recipient(bA, "60", null), recipient(bB, "30", null));

        assertCode(() -> update(AssetType.BANK_ACCOUNT, a), ErrorCode.ALLOCATION_PERCENT_INVALID);
        verify(assetAllocationRepository, never()).deleteByAssetId(any());
        verify(assetAllocationRepository, never()).save(any(AssetAllocation.class));
    }

    @Test
    @DisplayName("PROPORTIONAL: chỉ 1 người nhận → ALLOCATION_PERCENT_INVALID")
    void proportional_singleRecipient_throws() {
        var a = alloc(AllocationMode.PROPORTIONAL, recipient(bA, "100", null));

        assertCode(() -> update(AssetType.BANK_ACCOUNT, a), ErrorCode.ALLOCATION_PERCENT_INVALID);
    }

    @Test
    @DisplayName("PROPORTIONAL: thiếu hoặc ≤ 0% → ALLOCATION_PERCENT_INVALID")
    void proportional_missingOrZeroShare_throws() {
        var missing = alloc(AllocationMode.PROPORTIONAL, recipient(bA, null, null), recipient(bB, "100", null));
        var zero = alloc(AllocationMode.PROPORTIONAL, recipient(bA, "0", null), recipient(bB, "100", null));

        assertCode(() -> update(AssetType.BANK_ACCOUNT, missing), ErrorCode.ALLOCATION_PERCENT_INVALID);
        assertCode(() -> update(AssetType.BANK_ACCOUNT, zero), ErrorCode.ALLOCATION_PERCENT_INVALID);
    }

    @Test
    @DisplayName("PROPORTIONAL cho CRYPTO_WALLET → ALLOCATION_MODE_NOT_ALLOWED (dù tổng đúng 100%)")
    void proportional_cryptoWallet_notAllowed() {
        var a = alloc(AllocationMode.PROPORTIONAL, recipient(bA, "50", null), recipient(bB, "50", null));

        assertCode(() -> update(AssetType.CRYPTO_WALLET, a), ErrorCode.ALLOCATION_MODE_NOT_ALLOWED);
        verify(assetAllocationRepository, never()).save(any(AssetAllocation.class));
    }

    @Test
    @DisplayName("PROPORTIONAL: tài sản đã gán LEGAL_CLAIM_DOSSIER thì được phép dù assetType là gì")
    void proportional_legalClaimMechanism_allowed() throws Exception {
        asset.setTransferMechanism(TransferMechanism.LEGAL_CLAIM_DOSSIER);
        var a = alloc(AllocationMode.PROPORTIONAL, recipient(bA, "50", null), recipient(bB, "50", null));

        update(AssetType.CRYPTO_WALLET, a);

        verify(assetAllocationRepository, times(2)).save(any(AssetAllocation.class));
    }

    @Test
    @DisplayName("PROPORTIONAL: có người dự phòng → ALLOCATION_MODE_NOT_ALLOWED")
    void proportional_withContingent_notAllowed() {
        var a = alloc(AllocationMode.PROPORTIONAL, recipient(bA, "50", bC), recipient(bB, "50", null));

        assertCode(() -> update(AssetType.BANK_ACCOUNT, a), ErrorCode.ALLOCATION_MODE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("PROPORTIONAL: chia 3 phần 33.33 + 33.33 + 33.34 = 100 → ghi 3 dòng")
    void proportional_threeWaySplit_saved() throws Exception {
        var a = alloc(AllocationMode.PROPORTIONAL,
                recipient(bA, "33.33", null), recipient(bB, "33.33", null), recipient(bC, "33.34", null));

        update(AssetType.BANK_ACCOUNT, a);

        ArgumentCaptor<AssetAllocation> captor = ArgumentCaptor.forClass(AssetAllocation.class);
        verify(assetAllocationRepository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(r -> assertThat(r.getMode()).isEqualTo(AllocationMode.PROPORTIONAL));
        verify(entitlementService).assertCanUseAdvancedAllocation(ownerId);
    }

    // ===== EXCLUSIVE =====

    @Test
    @DisplayName("EXCLUSIVE hợp lệ có người dự phòng → xóa dòng cũ, ghi 1 dòng 100%")
    void exclusive_withContingent_saved() throws Exception {
        var a = alloc(AllocationMode.EXCLUSIVE, recipient(bA, null, bB));
        a.setNote("Giao cho con cả");

        update(AssetType.BANK_ACCOUNT, a);

        verify(assetAllocationRepository).deleteByAssetId(assetId);
        ArgumentCaptor<AssetAllocation> captor = ArgumentCaptor.forClass(AssetAllocation.class);
        verify(assetAllocationRepository).save(captor.capture());
        AssetAllocation saved = captor.getValue();
        assertThat(saved.getAsset()).isSameAs(asset);
        assertThat(saved.getBeneficiary()).isSameAs(bA);
        assertThat(saved.getContingentBeneficiary()).isSameAs(bB);
        assertThat(saved.getMode()).isEqualTo(AllocationMode.EXCLUSIVE);
        assertThat(saved.getSharePercent()).isEqualByComparingTo("100");
        assertThat(saved.getNote()).isEqualTo("Giao cho con cả");
        verify(entitlementService).assertCanUseAdvancedAllocation(ownerId);
    }

    @Test
    @DisplayName("EXCLUSIVE: người đủ điều kiện, không dự phòng → không cần quyền phân bổ nâng cao")
    void exclusive_withoutContingent_noAdvancedCheck() throws Exception {
        update(AssetType.CRYPTO_WALLET, alloc(AllocationMode.EXCLUSIVE, recipient(bA, null, null)));

        verify(assetAllocationRepository).save(any(AssetAllocation.class));
        verify(entitlementService, never()).assertCanUseAdvancedAllocation(any());
    }

    @Test
    @DisplayName("EXCLUSIVE: 2 người nhận → ASSET_FIELD_INVALID")
    void exclusive_twoRecipients_throws() {
        var a = alloc(AllocationMode.EXCLUSIVE, recipient(bA, null, null), recipient(bB, null, null));

        assertCode(() -> update(AssetType.BANK_ACCOUNT, a), ErrorCode.ASSET_FIELD_INVALID);
    }

    @Test
    @DisplayName("Người dự phòng trùng người chính → ASSET_FIELD_INVALID")
    void contingent_sameAsPrimary_throws() {
        var a = alloc(AllocationMode.EXCLUSIVE, recipient(bA, null, bA));

        assertCode(() -> update(AssetType.BANK_ACCOUNT, a), ErrorCode.ASSET_FIELD_INVALID);
    }

    @Test
    @DisplayName("Người dự phòng không thuộc vault → 404")
    void contingent_notInVault_throws404() {
        VaultBeneficiary outsider = beneficiary("outsider@test.com");   // không có trong danh sách của vault
        var a = alloc(AllocationMode.EXCLUSIVE, recipient(bA, null, outsider));

        assertStatus(() -> update(AssetType.BANK_ACCOUNT, a), HttpStatus.NOT_FOUND);
        verify(assetAllocationRepository, never()).save(any(AssetAllocation.class));
    }

    @Test
    @DisplayName("Người nhận chính không thuộc vault → 404")
    void primary_notInVault_throws404() {
        VaultBeneficiary outsider = beneficiary("outsider@test.com");
        var a = alloc(AllocationMode.EXCLUSIVE, recipient(outsider, null, null));

        assertStatus(() -> update(AssetType.BANK_ACCOUNT, a), HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Người nhận bị lặp → ASSET_FIELD_INVALID")
    void duplicateRecipient_throws() {
        var a = alloc(AllocationMode.PROPORTIONAL, recipient(bA, "50", null), recipient(bA, "50", null));

        assertCode(() -> update(AssetType.BANK_ACCOUNT, a), ErrorCode.ASSET_FIELD_INVALID);
    }

    // ===== JOINT_CONSENSUS, mode thiếu =====

    @Test
    @DisplayName("JOINT_CONSENSUS → ALLOCATION_MODE_NOT_ALLOWED cho đến khi có X1")
    void jointConsensus_notAllowed() {
        var a = alloc(AllocationMode.JOINT_CONSENSUS, recipient(bA, null, null));

        assertCode(() -> update(AssetType.BANK_ACCOUNT, a), ErrorCode.ALLOCATION_MODE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("Có recipients nhưng thiếu mode → ASSET_FIELD_INVALID")
    void missingMode_throws() {
        var a = alloc(null, recipient(bA, null, null));

        assertCode(() -> update(AssetType.BANK_ACCOUNT, a), ErrorCode.ASSET_FIELD_INVALID);
    }

    // ===== allocation null / rỗng =====

    @Test
    @DisplayName("allocation = null → giữ nguyên, không đụng bảng phân bổ, allocations rỗng")
    void allocationNull_leavesRowsUntouched() throws Exception {
        DigitalAssetResponse res = update(AssetType.BANK_ACCOUNT, null);

        verify(assetAllocationRepository, never()).deleteByAssetId(any());
        verify(assetAllocationRepository, never()).save(any(AssetAllocation.class));
        assertThat(res.getAllocations()).isEmpty();
    }

    @Test
    @DisplayName("recipients rỗng → xóa phân bổ riêng, quay về mặc định (thuộc tất cả)")
    void emptyRecipients_clearsRows() throws Exception {
        update(AssetType.BANK_ACCOUNT, alloc(AllocationMode.EXCLUSIVE));

        verify(assetAllocationRepository).deleteByAssetId(assetId);
        verify(assetAllocationRepository, never()).save(any(AssetAllocation.class));
    }

    // ===== đổi assetType khi còn PROPORTIONAL =====

    @Test
    @DisplayName("updateAsset: đổi sang CRYPTO_WALLET khi còn dòng PROPORTIONAL → ALLOCATION_MODE_NOT_ALLOWED")
    void update_typeChange_withExistingProportional_throws() {
        AssetAllocation old = AssetAllocation.builder()
                .asset(asset).beneficiary(bA)
                .mode(AllocationMode.PROPORTIONAL).sharePercent(new BigDecimal("60")).build();
        when(assetAllocationRepository.findByAssetId(assetId)).thenReturn(List.of(old));

        assertCode(() -> update(AssetType.CRYPTO_WALLET, null), ErrorCode.ALLOCATION_MODE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("updateAsset: giữ BANK_ACCOUNT, còn dòng PROPORTIONAL → bình thường, trả lại allocations")
    void update_sameType_withExistingProportional_ok() throws Exception {
        AssetAllocation old = AssetAllocation.builder()
                .asset(asset).beneficiary(bA)
                .mode(AllocationMode.PROPORTIONAL).sharePercent(new BigDecimal("60")).build();
        when(assetAllocationRepository.findByAssetId(assetId)).thenReturn(List.of(old));

        DigitalAssetResponse res = update(AssetType.BANK_ACCOUNT, null);

        assertThat(res.getAllocations()).hasSize(1);
        assertThat(res.getAllocations().get(0).getBeneficiaryId()).isEqualTo(bA.getId());
    }

    // ===== listAssets trả allocations =====

    @Test
    @DisplayName("listAssets: trả allocations[] của từng tài sản bằng một truy vấn gom")
    void listAssets_returnsAllocations() {
        AssetAllocation row = AssetAllocation.builder()
                .asset(asset).beneficiary(bA).contingentBeneficiary(bB)
                .mode(AllocationMode.EXCLUSIVE).build();
        when(digitalAssetRepository.findByVaultIdAndStatus(vaultId, AssetStatus.ACTIVE)).thenReturn(List.of(asset));
        when(assetAllocationRepository.findByAssetIdIn(anyCollection())).thenReturn(List.of(row));

        List<DigitalAssetResponse> result = assetService.listAssets(ownerId, vaultId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAllocations()).hasSize(1);
        DigitalAssetResponse.Allocation al = result.get(0).getAllocations().get(0);
        assertThat(al.getBeneficiaryId()).isEqualTo(bA.getId());
        assertThat(al.getContingentBeneficiaryId()).isEqualTo(bB.getId());
        assertThat(al.getMode()).isEqualTo(AllocationMode.EXCLUSIVE);
    }

    @Test
    @DisplayName("listAssets: tài sản không có dòng phân bổ → allocations rỗng")
    void listAssets_noRows_emptyAllocations() {
        when(digitalAssetRepository.findByVaultIdAndStatus(vaultId, AssetStatus.ACTIVE)).thenReturn(List.of(asset));
        when(assetAllocationRepository.findByAssetIdIn(anyCollection())).thenReturn(List.of());

        List<DigitalAssetResponse> result = assetService.listAssets(ownerId, vaultId);

        assertThat(result.get(0).getAllocations()).isEmpty();
    }
}