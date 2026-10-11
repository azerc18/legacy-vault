package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.HandoverStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO đại diện thông tin tổng quan tiến độ bàn giao từng Vault (FR-10).
 * Tuyệt đối không chứa secret/mật khẩu hay dữ liệu nhạy cảm của tài sản số.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VaultHandoverProgressResponse {
    private UUID vaultId;
    private String vaultName;
    private String description;
    private HandoverStatus handoverStatus;
    private long assetCount;
    private LocalDateTime claimDeadlineAt;
    private LocalDateTime unlockedAt;
    private String beneficiaryNameMasked;
    private String beneficiaryEmailMasked;
}
