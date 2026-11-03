package com.ltld.app.legacyvault.service.dmsservice;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * X2 (M3): DMS vừa chuyển PENDING_CONFIRMATION. Mail cho người xác nhận chéo và cho Owner được gửi SAU commit
 * bởi DmsConfirmationRequestListener; deadlineAt nhận diện đúng đợt xác nhận này.
 */
public record DmsConfirmationRequestedEvent(UUID configId, LocalDateTime deadlineAt) {
}
