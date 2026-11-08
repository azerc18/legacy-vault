package com.ltld.app.legacyvault.event;

import java.time.LocalDateTime;
import java.util.UUID;

/** Xác thực của tài sản sắp hết hạn (expired = false) hoặc đã hết hạn (expired = true). Mail N18 lắng nghe. */
public record AssetReverificationNeededEvent(UUID ownerId, UUID vaultId, UUID assetId, String assetName,
                                             LocalDateTime expiresAt, boolean expired) {
}
