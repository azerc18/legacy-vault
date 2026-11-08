package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.ExecutorTaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// F5: Executor đổi trạng thái và ghi chú; note null hoặc trống = xóa ghi chú
@Getter
@Setter
public class UpdateExecutorTaskRequest {
    @NotNull(message = "Vui lòng chọn trạng thái.")
    private ExecutorTaskStatus status;

    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự.")
    private String note;

    @Size(max = 200, message = "Mã tham chiếu tối đa 200 ký tự.")
    private String evidenceRef;
}
