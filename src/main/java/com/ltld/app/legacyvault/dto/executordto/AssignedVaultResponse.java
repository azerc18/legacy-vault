package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.VaultStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO đại diện một Vault mà Executor được ủy quyền quản lý (FR-08).
 * Chỉ chứa metadata, không giải mã bất kỳ secret/ghi chú nào của tài sản số.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignedVaultResponse {
    private UUID vaultId;
    private String vaultName;
    private String description;
    private VaultStatus vaultStatus;
    private UUID ownerId;
    private String ownerName;
    private long assetCount;
    private List<AssetType> assetTypes;
    private String beneficiaryNameMasked;
    private String beneficiaryEmailMasked;
    private ExecutorType executorType;
    private LocalDateTime activatedAt;
}
