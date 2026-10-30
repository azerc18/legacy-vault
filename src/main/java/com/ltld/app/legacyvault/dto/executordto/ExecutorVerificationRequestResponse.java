package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** FR-11: một hồ sơ xác minh Executor đã gửi, kể cả hồ sơ cũ đã bị từ chối. */
@Getter
@Builder
public class ExecutorVerificationRequestResponse {
    private final UUID id;
    private final LegalVerificationStatus status;
    private final List<VaultRef> vaults;
    private final String rejectionReason;
    private final LocalDateTime createdAt;
    private final LocalDateTime decidedAt;
    private final long unreadMessageCount;

    public record VaultRef(UUID id, String name) {
    }
}
