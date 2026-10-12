package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.SubmitLegalVerificationRequest;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationRequestResponseDto;

/**
 * Service cho FR-09: Executor gửi yêu cầu xác minh pháp lý (giấy chứng tử) để mở khóa Vault.
 */
public interface LegalVerificationSubmissionService {

    /**
     * Tạo yêu cầu xác minh pháp lý trạng thái PENDING cho các Vault Executor được ủy quyền.
     *
     * @param request       giấy chứng tử, danh sách Vault cần mở khóa, ghi chú (tùy chọn)
     * @param executorLogin email hoặc id của Executor đang đăng nhập
     * @return yêu cầu vừa tạo (không lộ đường dẫn tệp nội bộ)
     */
    VerificationRequestResponseDto submit(SubmitLegalVerificationRequest request, String executorLogin);
}
