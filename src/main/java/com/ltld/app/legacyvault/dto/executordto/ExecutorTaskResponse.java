package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.ExecutorTaskStatus;
import com.ltld.app.legacyvault.enums.ExecutorTaskWish;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

// F5: một việc trong checklist; helpUrl là trang hỗ trợ chính thức của nhà cung cấp (null nếu chưa có)
@Getter
@Builder
public class ExecutorTaskResponse {
    private final UUID id;
    private final UUID vaultId;
    private final String vaultName;
    private final AssetType assetType;
    private final String platform;
    private final String accountHandle;
    private final ExecutorTaskWish wish;
    private final ExecutorTaskStatus status;
    private final String note;
    private final String helpUrl;
    private final LocalDateTime updatedAt;
    private final LocalDateTime completedAt;
}
