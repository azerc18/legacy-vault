package com.ltld.app.legacyvault.event;

import java.util.UUID;

/** Executor vừa báo DONE (hoặc DONE lại sau khi bị phản đối). Mail N29 lắng nghe event này (AFTER_COMMIT). */
public record ExecutorTaskCompletedEvent(UUID taskId, UUID vaultId, UUID ownerId, UUID executorUserId,
                                         boolean hasEvidenceFile) {
}
