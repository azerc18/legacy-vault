package com.ltld.app.legacyvault.dto.beneficiarydto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssetDownloadResponse {
    private String fileName;
    // Nội dung đã giải mã, chỉ tồn tại trong bộ nhớ để gửi về client, không lưu lại
    private byte[] content;
}