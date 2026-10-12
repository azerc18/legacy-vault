package com.ltld.app.legacyvault.dto.beneficiarydto;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString(exclude = "content")
public class DocumentDownloadResponse {
    private final String fileName;
    private final String contentType;
    // Nội dung đã giải mã, chỉ tồn tại trong bộ nhớ để gửi về client, không lưu lại
    private final byte[] content;
}