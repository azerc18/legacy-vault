package com.ltld.app.legacyvault.dto.executordto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// C.6: Beneficiary phản đối kết quả Executor đã báo DONE
@Getter
@Setter
public class DisputeExecutorTaskRequest {
    @NotBlank(message = "Vui lòng nhập lý do phản đối.")
    @Size(min = 5, max = 500, message = "Lý do phản đối từ 5 đến 500 ký tự.")
    private String reason;
}
