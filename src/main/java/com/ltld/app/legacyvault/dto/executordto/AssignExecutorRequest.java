package com.ltld.app.legacyvault.dto.executordto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssignExecutorRequest {
    @Email(message = "Invalid email format")
    @NotBlank(message = "Executor email is required")
    @Size(max = 255, message = "Email must be at most 255 characters")
    private String email;

    // FR-06: vị trí đã có Executor khác thì Owner phải xác nhận thay thế
    private boolean confirmReplace;
}
