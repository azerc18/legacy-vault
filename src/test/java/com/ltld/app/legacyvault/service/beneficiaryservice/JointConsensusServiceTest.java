package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.entity.AssetAllocation;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.AllocationMode;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.exception.BeneficiaryException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.AssetAllocationRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.utility.ShamirSecretSharing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
@DisplayName("JointConsensusService – X1: đồng thuận K-of-N")
class JointConsensusServiceTest {

    @Mock private AssetAllocationRepository assetAllocationRepository;
    @Mock private CryptoService cryptoService;
    @InjectMocks private JointConsensusService service;

    private UUID assetId;
    private DigitalAsset asset;
    private VaultBeneficiary bA;
    private VaultBeneficiary bB;
    private VaultBeneficiary bC;
    private List<AssetAllocation> rows;

    @BeforeEach
    void setUp() throws Exception {
        assetId = UUID.randomUUID();
        asset = DigitalAsset.builder().id(assetId).build();
        bA = VaultBeneficiary.builder().id(UUID.randomUUID()).build();
        bB = VaultBeneficiary.builder().id(UUID.randomUUID()).build();
        bC = VaultBeneficiary.builder().id(UUID.randomUUID()).build();
        lenient().when(cryptoService.decrypt(any())).thenAnswer(inv -> ((String) inv.getArgument(0)).substring(4));

        // Khóa gốc thật, chia 2-of-3, mỗi dòng giữ 1 phần ("ENC:" giả lập mã hóa)
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        var shares = ShamirSecretSharing.split(key, 3, 2, new SecureRandom());
        rows = new ArrayList<>(List.of(row(bA, shares.get(0), key), row(bB, shares.get(1), key), row(bC, shares.get(2), key)));
        lenient().when(assetAllocationRepository.findByAssetId(assetId)).thenReturn(rows);
    }

    private AssetAllocation row(VaultBeneficiary b, ShamirSecretSharing.Share share, byte[] key) {
        return AssetAllocation.builder()
                .asset(asset).beneficiary(b)
                .mode(AllocationMode.JOINT_CONSENSUS).sharePercent(new BigDecimal("100"))
                .consensusThreshold(2)
                .consensusShare("ENC:" + share.encode())
                .consensusKeyHash(JointConsensusService.hashKey(key))
                .build();
    }

    @Test
    @DisplayName("Chưa ai đồng ý → assertReached ném CONSENSUS_NOT_REACHED")
    void noApproval_notReached() {
        assertThatThrownBy(() -> service.assertReached(assetId))
                .isInstanceOfSatisfying(BeneficiaryException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONSENSUS_NOT_REACHED));
    }

    @Test
    @DisplayName("Mới 1/2 người đồng ý → vẫn chưa đủ")
    void oneOfTwo_notReached() {
        JointConsensusService.Info info = service.approve(assetId, bA);

        assertThat(info.approved()).isEqualTo(1);
        assertThat(info.myApproved()).isTrue();
        assertThat(info.reached()).isFalse();
        assertThatThrownBy(() -> service.assertReached(assetId)).isInstanceOf(BeneficiaryException.class);
    }

    @Test
    @DisplayName("Đủ 2/3 người đồng ý và khóa ghép đúng → reached, assertReached không ném lỗi")
    void twoOfThree_reached() {
        service.approve(assetId, bA);
        JointConsensusService.Info info = service.approve(assetId, bC);

        assertThat(info.reached()).isTrue();
        assertThat(info.total()).isEqualTo(3);
        assertThat(info.threshold()).isEqualTo(2);
        service.assertReached(assetId);   // không ném lỗi
    }

    @Test
    @DisplayName("Đồng ý lặp lại không đổi thời điểm và không tăng số lượng")
    void approveTwice_isIdempotent() {
        service.approve(assetId, bA);
        LocalDateTime first = rows.get(0).getConsensusApprovedAt();

        JointConsensusService.Info info = service.approve(assetId, bA);

        assertThat(rows.get(0).getConsensusApprovedAt()).isEqualTo(first);
        assertThat(info.approved()).isEqualTo(1);
    }

    @Test
    @DisplayName("Hash khóa bị sửa → dù đủ K người đồng ý vẫn không mở")
    void tamperedKeyHash_notReached() {
        rows.forEach(r -> r.setConsensusKeyHash("0".repeat(64)));
        service.approve(assetId, bA);
        JointConsensusService.Info info = service.approve(assetId, bB);

        assertThat(info.approved()).isEqualTo(2);
        assertThat(info.reached()).isFalse();
    }

    @Test
    @DisplayName("Tài sản thường (EXCLUSIVE): assertReached bỏ qua, approve → CONSENSUS_NOT_APPLICABLE")
    void notJoint_noGate() {
        AssetAllocation plain = AssetAllocation.builder()
                .asset(asset).beneficiary(bA).mode(AllocationMode.EXCLUSIVE).build();
        when(assetAllocationRepository.findByAssetId(assetId)).thenReturn(List.of(plain));

        service.assertReached(assetId);   // không ném lỗi
        assertThatThrownBy(() -> service.approve(assetId, bA))
                .isInstanceOfSatisfying(BeneficiaryException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONSENSUS_NOT_APPLICABLE));
        verify(assetAllocationRepository, never()).save(any(AssetAllocation.class));
    }

    @Test
    @DisplayName("Tài sản chưa có dòng phân bổ nào: assertReached bỏ qua")
    void noRows_noGate() {
        when(assetAllocationRepository.findByAssetId(assetId)).thenReturn(List.of());

        service.assertReached(assetId);
    }

    @Test
    @DisplayName("Người không nằm trong danh sách nhận → ASSET_NOT_ALLOCATED_TO_YOU")
    void outsider_cannotApprove() {
        VaultBeneficiary outsider = VaultBeneficiary.builder().id(UUID.randomUUID()).build();

        assertThatThrownBy(() -> service.approve(assetId, outsider))
                .isInstanceOfSatisfying(BeneficiaryException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ASSET_NOT_ALLOCATED_TO_YOU));
    }

    // ---- Review PR #164, mục 3: người đã từ chối không được đếm ----

    @Test
    @DisplayName("Người đã đồng ý rồi bị DECLINED thì không còn được đếm")
    void declinedApprover_notCounted() {
        service.approve(assetId, bA);
        service.approve(assetId, bB);
        bB.setStatus(BeneficiaryInviteStatus.DECLINED);

        JointConsensusService.Info info = service.infoOf(rows, bA.getId());

        assertThat(info.approved()).isEqualTo(1);
        assertThat(info.reached()).isFalse();
        assertThatThrownBy(() -> service.assertReached(assetId)).isInstanceOf(BeneficiaryException.class);
    }

    @Test
    @DisplayName("Người từ bỏ (WAIVED) cũng không được đếm")
    void waivedApprover_notCounted() {
        service.approve(assetId, bA);
        service.approve(assetId, bC);
        bC.setStatus(BeneficiaryInviteStatus.WAIVED);

        assertThat(service.infoOf(rows, bA.getId()).reached()).isFalse();
    }

    // ---- Review PR #164, mục 1: kẹt khi còn nhận được < K ----

    @Test
    @DisplayName("2-of-3: một người từ chối thì chưa kẹt, hai người từ chối thì kẹt")
    void isStalled_onlyWhenFewerThanKLeft() {
        assertThat(service.isStalled(rows)).isFalse();

        bA.setStatus(BeneficiaryInviteStatus.DECLINED);
        assertThat(service.isStalled(rows)).isFalse();

        bB.setStatus(BeneficiaryInviteStatus.DECLINED);
        assertThat(service.isStalled(rows)).isTrue();
    }

    @Test
    @DisplayName("Tài sản thường không bao giờ bị coi là joint-stalled")
    void isStalled_falseForNonJoint() {
        AssetAllocation plain = AssetAllocation.builder()
                .asset(asset).beneficiary(bA).mode(AllocationMode.EXCLUSIVE).build();
        bA.setStatus(BeneficiaryInviteStatus.DECLINED);

        assertThat(service.isStalled(List.of(plain))).isFalse();
    }

    @Test
    @DisplayName("countStalledAssets đếm số tài sản joint thiếu người trong vault")
    void countStalledAssets_countsOnlyStalled() {
        UUID vaultId = UUID.randomUUID();
        when(assetAllocationRepository.findByVaultId(vaultId)).thenReturn(rows);
        assertThat(service.countStalledAssets(vaultId)).isZero();

        bA.setStatus(BeneficiaryInviteStatus.DECLINED);
        bB.setStatus(BeneficiaryInviteStatus.DECLINED);
        assertThat(service.countStalledAssets(vaultId)).isEqualTo(1);
    }

    // ---- Review PR #164, mục 2: đóng hồ sơ = đồng ý ----

    @Test
    @DisplayName("approveAllHeldBy ghi nhận đồng ý cho tài sản joint đang giữ, gọi lại thì không ghi thêm")
    void approveAllHeldBy_approvesOnce() {
        UUID vaultId = UUID.randomUUID();
        when(assetAllocationRepository.findHeldBy(vaultId, bA.getId())).thenReturn(List.of(rows.get(0)));

        int first = service.approveAllHeldBy(vaultId, bA);
        int second = service.approveAllHeldBy(vaultId, bA);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(rows.get(0).getConsensusApprovedAt()).isNotNull();
        verify(assetAllocationRepository, times(1)).save(rows.get(0));
    }

    @Test
    @DisplayName("approveAllHeldBy bỏ qua tài sản không phải joint")
    void approveAllHeldBy_ignoresNonJoint() {
        UUID vaultId = UUID.randomUUID();
        AssetAllocation plain = AssetAllocation.builder()
                .asset(asset).beneficiary(bA).mode(AllocationMode.EXCLUSIVE).build();
        when(assetAllocationRepository.findHeldBy(vaultId, bA.getId())).thenReturn(List.of(plain));

        assertThat(service.approveAllHeldBy(vaultId, bA)).isZero();
        verify(assetAllocationRepository, never()).save(any(AssetAllocation.class));
    }
}
