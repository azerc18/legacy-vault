package com.ltld.app.legacyvault.service.handoverservice;

import com.ltld.app.legacyvault.dto.assetverificationdto.AssetVerificationSummary;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** Nội dung đọc được của bản chụp: hash, thời điểm chụp và trạng thái xác thực của từng tài sản lúc đó. */
public record HandoverSnapshotView(UUID vaultId, String hash, int assetCount, LocalDateTime createdAt,
                                   Map<UUID, AssetVerificationSummary> verification) {
}
