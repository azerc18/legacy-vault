package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.executordto.ExecutorActivationResponse;

import java.time.LocalDateTime;
import java.util.UUID;

public interface DmsAutomationService {

    /** Quét các DMS ACTIVE đã quá hạn check-in và kích hoạt (FR-07 bước 1-3). */
    void processOverdueDms(LocalDateTime now);

    /** Leo thang sang Backup nếu Primary không xác nhận sau thời gian chờ (FR-07 ngoại lệ 3a, BR-03). */
    void escalateUnresponsivePrimaries(LocalDateTime now);

    /**
     * Executor xác nhận đã nhận thông báo kích hoạt (FR-07 bước 4).
     *
     * @param assignmentId  id của ExecutorAssignment
     * @param executorLogin định danh người đang đăng nhập (email hoặc user id lấy từ phiên)
     */
    ExecutorActivationResponse acknowledgeActivation(UUID assignmentId, String executorLogin);
}
