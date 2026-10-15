package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.DocumentType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

// Thông tin tóm tắt tài liệu pháp lý của vault. Không có đường dẫn lưu trữ và không có nội dung.
@Data
@Builder
public class LegalDocumentSummaryResponse {
    private UUID id;
    private DocumentType documentType;
    private String fileName;
    private LocalDateTime uploadedAt;
}