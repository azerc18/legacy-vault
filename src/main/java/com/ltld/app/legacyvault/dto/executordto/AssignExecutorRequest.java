package com.ltld.app.legacyvault.dto.executordto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssignExecutorRequest {
    @Email(message = "Email không đúng định dạng")
    @NotBlank(message = "Email Executor là bắt buộc")
    private String email;

    // FR-06: vị trí đã có Executor khác thì Owner phải xác nhận thay thế
    private boolean confirmReplace;
}
