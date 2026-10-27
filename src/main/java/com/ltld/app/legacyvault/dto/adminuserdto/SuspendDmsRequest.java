package com.ltld.app.legacyvault.dto.adminuserdto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SuspendDmsRequest {
    @Size(max = 255, message = "Reason must be at most 255 characters")
    private String reason;
}
