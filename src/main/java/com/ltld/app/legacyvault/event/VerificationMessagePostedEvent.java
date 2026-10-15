package com.ltld.app.legacyvault.event;

import java.util.UUID;

public record VerificationMessagePostedEvent(UUID requestId, UUID ownerId, UUID executorId, UUID verifierId) {
}
