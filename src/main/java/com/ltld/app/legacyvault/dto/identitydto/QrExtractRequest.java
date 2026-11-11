package com.ltld.app.legacyvault.dto.identitydto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QrExtractRequest {
    @NotBlank(message = "QR content is required")
    @Size(max = 1024, message = "QR content is too long")
    private String qrText;
}
