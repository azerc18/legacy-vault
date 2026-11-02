package com.ltld.app.legacyvault.dto.documentdto;

import com.ltld.app.legacyvault.enums.DocumentType;
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
public class DocumentResponse {
    private UUID id;
    private UUID vaultId;
    private UUID documentGroupId;
    private DocumentType documentType;
    private String fileName;
    private LocalDateTime uploadedAt;
    private int version;
    private boolean isCurrent;
    private long totalVersions;
    /** true khi Vault ở trạng thái ARCHIVED_LOCKED — FE dùng để ẩn nút tải tài liệu. */
    private boolean archived;
}
