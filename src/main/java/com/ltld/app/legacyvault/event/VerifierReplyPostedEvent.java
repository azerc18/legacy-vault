package com.ltld.app.legacyvault.event;

import java.util.UUID;

public record VerifierReplyPostedEvent(UUID requestId, UUID executorId) {
}