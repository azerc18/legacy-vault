package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.entity.AssetAllocation;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.AllocationMode;
import com.ltld.app.legacyvault.exception.BeneficiaryException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.AssetAllocationRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.utility.ShamirSecretSharing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * X1: đồng thuận K-of-N cho tài sản JOINT_CONSENSUS.
 * Mỗi người nhận có 1 phần khóa Shamir (lưu mã hóa). Tài sản chỉ được mở khi có ít nhất K người đồng ý
 * VÀ ghép K phần khóa của họ ra đúng khóa gốc (so hash), tức dữ liệu phân bổ không bị sửa trái phép.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JointConsensusService {

    private final AssetAllocationRepository assetAllocationRepository;
    private final CryptoService cryptoService;

    /** Trạng thái đồng thuận của một tài sản đối với một người nhận. */
    public record Info(boolean joint, int threshold, int total, int approved, boolean myApproved, boolean reached) {
        public static Info notJoint() {
            return new Info(false, 0, 0, 0, false, false);
        }
    }

    public static String hashKey(byte[] key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Tài sản có phải JOINT_CONSENSUS không (mọi dòng phân bổ cùng một mode). */
    public boolean isJoint(List<AssetAllocation> rows) {
        return !rows.isEmpty() && rows.get(0).getMode() == AllocationMode.JOINT_CONSENSUS;
    }

    public Info infoOf(List<AssetAllocation> rows, UUID myBeneficiaryId) {
        if (!isJoint(rows)) {
            return Info.notJoint();
        }
        int threshold = rows.get(0).getConsensusThreshold() == null ? 0 : rows.get(0).getConsensusThreshold();
        int approved = (int) rows.stream().filter(r -> r.getConsensusApprovedAt() != null).count();
        boolean mine = myBeneficiaryId != null && rows.stream()
                .anyMatch(r -> r.getBeneficiary().getId().equals(myBeneficiaryId) && r.getConsensusApprovedAt() != null);
        return new Info(true, threshold, rows.size(), approved, mine, isReached(rows, threshold));
    }

    /** Tính trạng thái cho nhiều tài sản cùng lúc (1 query đã nạp sẵn rows). */
    public Map<UUID, Info> infoByAsset(List<AssetAllocation> allRows, UUID myBeneficiaryId) {
        Map<UUID, List<AssetAllocation>> byAsset = allRows.stream()
                .collect(Collectors.groupingBy(r -> r.getAsset().getId()));
        return byAsset.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> infoOf(e.getValue(), myBeneficiaryId)));
    }

    /** Chặn xem/tải nếu tài sản JOINT_CONSENSUS chưa đủ K đồng ý. Tài sản thường thì bỏ qua. */
    public void assertReached(UUID assetId) {
        List<AssetAllocation> rows = assetAllocationRepository.findByAssetId(assetId);
        if (!isJoint(rows)) {
            return;
        }
        Info info = infoOf(rows, null);
        if (!info.reached()) {
            throw new BeneficiaryException(ErrorCode.CONSENSUS_NOT_REACHED,
                    "This asset needs approval from " + info.threshold() + " of " + info.total()
                            + " recipients (" + info.approved() + " so far).");
        }
    }

    /** Ghi nhận người nhận đồng ý, trả về trạng thái mới. Gọi lặp lại là vô hại. */
    public Info approve(UUID assetId, VaultBeneficiary me) {
        List<AssetAllocation> rows = assetAllocationRepository.findByAssetId(assetId);
        if (!isJoint(rows)) {
            throw new BeneficiaryException(ErrorCode.CONSENSUS_NOT_APPLICABLE,
                    "This asset does not require joint consensus.");
        }
        AssetAllocation mine = rows.stream()
                .filter(r -> r.getBeneficiary().getId().equals(me.getId()))
                .findFirst()
                .orElseThrow(() -> new BeneficiaryException(ErrorCode.ASSET_NOT_ALLOCATED_TO_YOU,
                        "This asset is not allocated to you."));
        if (mine.getConsensusApprovedAt() == null) {
            mine.setConsensusApprovedAt(LocalDateTime.now());
            assetAllocationRepository.save(mine);
        }
        return infoOf(rows, me.getId());
    }

    // Đủ K người đồng ý và ghép K phần khóa của họ ra đúng khóa gốc
    private boolean isReached(List<AssetAllocation> rows, int threshold) {
        List<AssetAllocation> approvers = rows.stream()
                .filter(r -> r.getConsensusApprovedAt() != null)
                .limit(threshold)
                .toList();
        if (threshold < 2 || approvers.size() < threshold) {
            return false;
        }
        try {
            List<ShamirSecretSharing.Share> shares = approvers.stream()
                    .map(r -> {
                        try {
                            return ShamirSecretSharing.Share.decode(cryptoService.decrypt(r.getConsensusShare()));
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }).toList();
            byte[] key = ShamirSecretSharing.combine(shares);
            return hashKey(key).equals(rows.get(0).getConsensusKeyHash());
        } catch (RuntimeException e) {
            // Không log phần khóa, chỉ log id tài sản
            log.error("Consensus key check failed for asset {}", rows.get(0).getAsset().getId(), e);
            throw new BeneficiaryException("Could not verify the consensus. Please try again later.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
