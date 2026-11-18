package com.ltld.app.legacyvault.dto.legalverificationdto;

import com.ltld.app.legacyvault.enums.SignatureMethod;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SignVerificationRequestDto {

    @NotNull(message = "Please select a signing method")
    private SignatureMethod method;

    // Bắt buộc khi method = MOCK_OTP (kiểm trong service vì phụ thuộc method)
    @Pattern(regexp = "\\d{6}", message = "Confirmation code must be exactly 6 digits")
    private String code;

    // Bắt buộc khi method = HANDWRITTEN: ảnh PNG Base64, có thể kèm tiền tố "data:image/png;base64,".
    // ~270 KB Base64 ứng với ảnh 200 KB, giới hạn thật nằm ở HandwrittenSignatureImage.
    @Size(max = 300_000, message = "The signature image is too large")
    private String signatureImage;
}