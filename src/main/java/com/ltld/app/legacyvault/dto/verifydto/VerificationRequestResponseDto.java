package com.ltld.app.legacyvault.dto;

import com.ltld.app.legacyvault.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationRequestResponseDto {
    private UUID id;
    private String ownerName;
    private String executorName;
    private VerificationStatus status;
    private String deathCertificateFileUrlEncrypted;
    private String rejectionReason;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
}