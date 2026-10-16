package com.ltld.app.legacyvault.dto.documentdto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DocumentDownloadResponse {
    private final String fileName;
    private final String contentType;
    private final byte[] content;
}
