package com.ltld.app.legacyvault.dto.legalverificationdto;

import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationRequestResponseDto {
    private UUID id;
    private String ownerName;
    private String executorName;
    private LegalVerificationStatus status;
    private boolean hasDeathCertificate;   // true/false thay vì lộ URL nội bộ
    // Giữ để FE cũ không vỡ; bỏ khi mọi FE đã dùng `vaults` (tránh giữ hai trường mãi)
    private List<UUID> vaultIds;           // verifier biết sẽ mở khóa vault nào
    private List<VerificationVaultDto> vaults; // cùng danh sách, kèm tên vault
    private String rejectionReason;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
}