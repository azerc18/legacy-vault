package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** Kết quả Executor xác nhận đã nhận thông báo kích hoạt (FR-07). Id là id của ExecutorAssignment. */
@Data
@Builder
public class ExecutorActivationResponse {
    private UUID id;
    private ExecutorType executorType;
    private ExecutorStatus status;
    private LocalDateTime activatedAt;
    private LocalDateTime acknowledgedAt;
}
