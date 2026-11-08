package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.AllocationMode;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.AssetVerificationStatus;
import com.ltld.app.legacyvault.enums.DocumentType;
import com.ltld.app.legacyvault.enums.TransferMechanism;
import com.ltld.app.legacyvault.enums.VerificationProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// X4: dữ liệu để vẽ hồ sơ khai nhận. KHÔNG chứa trường bí mật.
public record InheritanceDossierData(
        UUID vaultId,
        String vaultName,
        LocalDateTime unlockedAt,
        LocalDateTime claimDeadlineAt,
        Person owner,
        Person beneficiary,
        Person executor,          // null nếu không có
        Signature signature,      // null nếu chưa có Verifier ký
        List<AssetLine> assets,
        List<DocumentLine> documents,
        LocalDateTime generatedAt,
        String handoverSnapshotHash,        // PR 2: null nếu chưa có bản chụp lúc bàn giao
        LocalDateTime handoverSnapshotAt) {

    // Giữ chữ ký 11 tham số cũ (test, nơi gọi cũ): chưa có bản chụp bàn giao
    public InheritanceDossierData(UUID vaultId, String vaultName, LocalDateTime unlockedAt,
                                  LocalDateTime claimDeadlineAt, Person owner, Person beneficiary,
                                  Person executor, Signature signature, List<AssetLine> assets,
                                  List<DocumentLine> documents, LocalDateTime generatedAt) {
        this(vaultId, vaultName, unlockedAt, claimDeadlineAt, owner, beneficiary, executor, signature,
                assets, documents, generatedAt, null, null);
    }

    public record Person(String fullName, String email) {}

    public record Signature(String verifierName, String verifierEmail, String method,
                            String hash, LocalDateTime signedAt, UUID requestId) {}

    /** Trạng thái xác thực của tài sản TẠI THỜI ĐIỂM BÀN GIAO (lấy từ bản chụp), không phải trạng thái hiện tại. */
    public record Verification(AssetVerificationStatus status, AssetVerificationMethod method,
                               VerificationProvider provider, LocalDateTime verifiedAt) {}

    public record AssetLine(String assetName, AssetType type, TransferMechanism mechanism,
                            AllocationMode mode, BigDecimal sharePercent, String note,
                            boolean viaFallback,
                            Map<String, String> publicFields,   // key -> value, đúng thứ tự schema, đã che số TK
                            Verification verification) {        // null = không có thông tin xác thực

        // Giữ chữ ký 8 tham số cũ
        public AssetLine(String assetName, AssetType type, TransferMechanism mechanism,
                         AllocationMode mode, BigDecimal sharePercent, String note,
                         boolean viaFallback, Map<String, String> publicFields) {
            this(assetName, type, mechanism, mode, sharePercent, note, viaFallback, publicFields, null);
        }
    }

    public record DocumentLine(String fileName, DocumentType type, LocalDateTime uploadedAt) {}
}
