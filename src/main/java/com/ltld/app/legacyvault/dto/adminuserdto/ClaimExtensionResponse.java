package com.ltld.app.legacyvault.dto.adminuserdto;

import java.time.LocalDateTime;
import java.util.UUID;

// ClaimExtensionResponse.java
public record ClaimExtensionResponse(UUID vaultId, LocalDateTime claimDeadlineAt, int claimExtensions, int remaining) {}
