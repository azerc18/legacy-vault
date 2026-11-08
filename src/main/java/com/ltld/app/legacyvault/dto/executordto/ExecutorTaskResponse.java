package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.ExecutorTaskStatus;
import com.ltld.app.legacyvault.enums.ExecutorTaskWish;
import com.ltld.app.legacyvault.enums.TaskConfirmationStatus;
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
    private final String evidenceRef;
    private final boolean hasEvidenceDocument;
    // PR 2 (C.4): tên tệp và SHA-256 của tệp bằng chứng (null nếu chưa có tệp)
    private final String evidenceFileName;
    private final String evidenceSha256;
    // PR 2 (C.6): xác nhận chéo. confirmationStatus null = chưa DONE hoặc DONE từ trước PR 2
    private final TaskConfirmationStatus confirmationStatus;
    private final LocalDateTime confirmDeadlineAt;
    private final LocalDateTime confirmedAt;
    private final String disputeReason;
    private final LocalDateTime disputedAt;
    // PENDING nhưng đã quá hạn phản đối: coi như đã được chấp nhận (tính khi đọc)
    private final boolean autoConfirmed;
}
