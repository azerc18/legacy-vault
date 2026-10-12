package com.ltld.app.legacyvault.dto.executordto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Request (multipart/form-data) Executor gửi yêu cầu xác minh pháp lý kèm giấy chứng tử (FR-09).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitLegalVerificationRequest {

    @NotNull(message = "Vui lòng tải lên giấy chứng tử")
    private MultipartFile deathCertificate;

    @NotEmpty(message = "Vui lòng chọn ít nhất một Vault cần mở khóa")
    @Size(max = 50, message = "Số lượng Vault vượt quá giới hạn cho phép")
    private List<@NotNull UUID> vaultIds;

    @Size(max = 2000, message = "Ghi chú không được vượt quá 2000 ký tự")
    private String note;
}
