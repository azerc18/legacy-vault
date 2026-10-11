package com.ltld.app.legacyvault.dto.executordto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request gửi tin nhắn hoặc tài liệu bổ sung trong yêu cầu xác minh (FR-11).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendVerificationMessageRequest {

    @Size(max = 2000, message = "Nội dung tin nhắn không được vượt quá 2000 ký tự")
    private String messageContent;

    @Size(max = 500, message = "URL tệp đính kèm không hợp lệ")
    private String attachmentUrl;

    @Size(max = 255, message = "Tên tệp không hợp lệ")
    private String attachmentName;

    private Long attachmentSize;
}
