package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.SendVerificationMessageRequest;
import com.ltld.app.legacyvault.dto.executordto.VerificationMessageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** FR-11: Executor trao đổi nội dung và tệp bổ sung với Legal Verifier trong một yêu cầu xác minh của mình. */
public interface VerificationExchangeService {

    VerificationMessageResponse send(UUID assignmentId, UUID requestId, String executorLogin,
                                     MultipartFile file, SendVerificationMessageRequest request);

    List<VerificationMessageResponse> history(UUID assignmentId, UUID requestId, String executorLogin);
}
