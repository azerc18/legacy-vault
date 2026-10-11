package com.ltld.app.legacyvault.dto.legalverificationdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectVerificationRequestDto {

    @NotBlank(message = "Vui lòng nhập lý do từ chối")
    @Size(max = 1000, message = "Lý do tối đa 1000 ký tự")
    private String reason;
}