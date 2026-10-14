package com.ltld.app.legacyvault.service.executorservice;

// FR-08: Service xử lý nộp yêu cầu xác minh cái chết của Executor
import com.ltld.app.legacyvault.dto.executordto.CreateDeathVerificationRequest;
import com.ltld.app.legacyvault.dto.executordto.DeathVerificationResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ExecutorVerificationService {

    /**
     * FR-08: Executor nộp hồ sơ xác minh cái chết (Death Verification Request).
     * 404: assignment không tồn tại hoặc không phải của người gọi.
     * 403: Executor chưa ACTIVE.
     * 409: DMS chưa TRIGGERED, hoặc đã có yêu cầu PENDING cho Owner này.
     * 400: file không hợp lệ (rỗng, >10MB, không đúng định dạng pdf/png/jpg).
     */
    DeathVerificationResponse submit(UUID assignmentId, String executorLogin,
                                     MultipartFile file, CreateDeathVerificationRequest request);
}
