package com.ltld.app.legacyvault.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectVerificationRequestDto {

    @NotBlank(message = "Vui lòng nhập lý do từ chối")
    private String reason;
}