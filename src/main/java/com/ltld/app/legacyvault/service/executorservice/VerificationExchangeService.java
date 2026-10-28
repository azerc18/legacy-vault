package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.SendVerificationMessageRequest;
import com.ltld.app.legacyvault.dto.executordto.VerificationMessageResponse;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationDocumentDto;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** FR-11: Executor trao đổi nội dung và tệp bổ sung với Legal Verifier trong một yêu cầu xác minh của mình. */
public interface VerificationExchangeService {

    VerificationMessageResponse send(UUID assignmentId, UUID requestId, String executorLogin,
                                     MultipartFile file, SendVerificationMessageRequest request);

    /** B6: phân trang, mới nhất trước; đồng thời đánh dấu Executor đã đọc tin của Verifier. */
    Page<VerificationMessageResponse> history(UUID assignmentId, UUID requestId, String executorLogin,
                                              int page, int size);

    /** B5: tải tệp đính kèm (đã giải mã) của một tin nhắn trong yêu cầu xác minh của mình. */
    VerificationDocumentDto getAttachment(UUID assignmentId, UUID requestId, UUID messageId, String executorLogin);
}
