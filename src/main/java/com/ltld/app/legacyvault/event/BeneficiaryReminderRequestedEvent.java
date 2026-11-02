package com.ltld.app.legacyvault.event;

import java.util.UUID;

/**
 * N2-exec: Executor nhắc một người thụ hưởng chưa phản hồi.
 * Listener: BeneficiaryReminderMailListener gửi mail sau commit (@TransactionalEventListener(AFTER_COMMIT) + @Async).
 */
public record BeneficiaryReminderRequestedEvent(UUID vaultBeneficiaryId) {
}
