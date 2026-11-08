package com.ltld.app.legacyvault.dto.adminoverviewdto;

import com.ltld.app.legacyvault.enums.VaultStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** B2: một dòng trên trang Kho của Admin (chỉ thông tin quản trị, không có tài sản). */
@Getter
@Builder
public class AdminVaultRowResponse {
    private UUID id;
    private String name;
    private String description;
    private VaultStatus status;
    private UUID ownerId;
    private String ownerEmail;
    private String ownerName;
    private LocalDateTime unlockedAt;
    private LocalDateTime claimDeadlineAt;
    private int claimExtensions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
