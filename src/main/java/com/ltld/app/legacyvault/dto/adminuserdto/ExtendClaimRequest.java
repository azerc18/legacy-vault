package com.ltld.app.legacyvault.dto.adminuserdto;

import jakarta.validation.constraints.*;

// ExtendClaimRequest.java
public record ExtendClaimRequest(
        @NotNull @Min(value = 1, message = "At least 1 day") @Max(value = 30, message = "At most 30 days") Integer days,
        @NotBlank(message = "Reason is required") @Size(max = 255) String reason) {}