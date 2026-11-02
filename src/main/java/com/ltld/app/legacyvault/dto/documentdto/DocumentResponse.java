package com.ltld.app.legacyvault.dto.documentdto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    /** X5 (m1): Xuất JSON là "isCurrent" (khớp cột DB is_current và FE v.isCurrent). */
    @JsonProperty("isCurrent")
    private boolean current;
    private long totalVersions;
    /** true khi Vault ở trạng thái ARCHIVED_LOCKED — FE dùng để ẩn nút tải tài liệu. */
    private boolean archived;
}
