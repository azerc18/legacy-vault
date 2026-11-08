package com.ltld.app.legacyvault.event;

import java.util.UUID;

/** Beneficiary phản đối kết quả DONE của Executor. */
public record ExecutorTaskDisputedEvent(UUID taskId, UUID vaultId, UUID ownerId, String vaultName,
                                        String platform, String reason) {
}
