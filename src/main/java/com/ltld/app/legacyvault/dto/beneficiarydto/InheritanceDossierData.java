package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.AllocationMode;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.DocumentType;
import com.ltld.app.legacyvault.enums.TransferMechanism;

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
        LocalDateTime generatedAt) {

    public record Person(String fullName, String email) {}

    public record Signature(String verifierName, String verifierEmail, String method,
                            String hash, LocalDateTime signedAt, UUID requestId) {}

    public record AssetLine(String assetName, AssetType type, TransferMechanism mechanism,
                            AllocationMode mode, BigDecimal sharePercent, String note,
                            boolean viaFallback,
                            Map<String, String> publicFields) {}   // key -> value, đúng thứ tự schema, đã che số TK

    public record DocumentLine(String fileName, DocumentType type, LocalDateTime uploadedAt) {}
}