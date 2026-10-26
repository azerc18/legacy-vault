package com.ltld.app.legacyvault.event;

import com.ltld.app.legacyvault.enums.LegalVerificationStatus;

import java.util.UUID;

/**
 * B-04: Event phát ra khi Legal Verifier duyệt (APPROVED) hoặc từ chối (REJECTED) hồ sơ.
 * Listener gửi mail cho Executor lắng nghe bằng @TransactionalEventListener(AFTER_COMMIT),
 * nên lỗi gửi mail không làm hỏng quyết định của Verifier.
 * reasonLabel: lý do từ chối (null khi APPROVED).
 */
public record VerificationRequestDecidedEvent(UUID requestId, LegalVerificationStatus status, String reasonLabel) {
}
