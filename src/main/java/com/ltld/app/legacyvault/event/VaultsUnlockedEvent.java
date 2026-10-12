package com.ltld.app.legacyvault.event;

import java.util.List;
import java.util.UUID;

/**
 * Event phát ra khi Legal Verifier ký mở khóa thành công các Vault trong hồ sơ.
 * Module FR-16 (Beneficiary Notification) và FR-07 (Executor) sẽ lắng nghe event này
 * để gửi email/thông báo thông qua @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT).
 */
public record VaultsUnlockedEvent(UUID requestId, List<UUID> vaultIds) {
}
