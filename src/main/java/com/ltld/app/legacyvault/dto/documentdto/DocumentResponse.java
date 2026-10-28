package com.ltld.app.legacyvault.dto.documentdto;

import com.ltld.app.legacyvault.enums.DocumentType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DocumentResponse {
    private UUID id;
    private UUID vaultId;
    private DocumentType documentType;
    private String fileName;
    private LocalDateTime uploadedAt;
    /** true khi Vault ở trạng thái ARCHIVED_LOCKED — FE dùng để ẩn nút tải tài liệu. */
    private boolean archived;
}
