package com.ltld.app.legacyvault.event;

import com.ltld.app.legacyvault.enums.LegalVerificationStatus;

import java.util.UUID;

/**
 * FR-12: Event phát ra khi Legal Verifier duyệt (APPROVED) hoặc từ chối (REJECTED) hồ sơ.
 * Listener: VerificationDecisionMailListener gửi mail cho Executor (@TransactionalEventListener(AFTER_COMMIT)
 * + @Async nên lỗi gửi mail không làm hỏng quyết định của Verifier).
 * reason: lý do từ chối do Verifier nhập/chọn (null khi APPROVED).
 */
public record VerificationRequestDecidedEvent(UUID requestId, LegalVerificationStatus status, String reason) {
}
