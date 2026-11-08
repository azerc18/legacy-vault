package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.DocumentDownloadResponse;
import com.ltld.app.legacyvault.dto.executordto.ExecutorTaskResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

// C.4: tệp bằng chứng của task Executor, lưu qua kho tài liệu mã hóa
public interface ExecutorEvidenceService {

    /** Tải (hoặc thay) tệp bằng chứng của task. */
    ExecutorTaskResponse upload(UUID assignmentId, UUID taskId, MultipartFile file, String executorLogin)
            throws Exception;

    /** Executor tải lại tệp bằng chứng đã giải mã. */
    DocumentDownloadResponse download(UUID assignmentId, UUID taskId, String executorLogin) throws Exception;
}

