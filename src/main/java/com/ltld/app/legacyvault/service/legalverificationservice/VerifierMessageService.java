package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.dto.executordto.SendVerificationMessageRequest;
import com.ltld.app.legacyvault.dto.executordto.VerificationMessageResponse;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationDocumentDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface VerifierMessageService {

    List<VerificationMessageResponse> history(UUID requestId, UUID verifierId);

    VerificationMessageResponse send(UUID requestId, UUID verifierId,
                                     MultipartFile file, SendVerificationMessageRequest request);

    VerificationDocumentDto getAttachment(UUID requestId, UUID messageId, UUID verifierId);
}