package com.ltld.app.legacyvault.dto.adminuserdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserStatusRequest {
    @NotBlank(message = "This field is required")
    private String status;

    @Size(max = 255, message = "At most 255 characters")
    private String reason;
}
