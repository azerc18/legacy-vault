package com.ltld.app.legacyvault.dto.adminuserdto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SuspendDmsRequest {
    @Size(max = 255, message = "Lý do tối đa 255 ký tự")
    private String reason;
}
