package com.ltld.app.legacyvault.event;

import java.util.UUID;

public record VerificationRequestSubmittedEvent(UUID requestId, UUID ownerId, UUID executorId) {
}
