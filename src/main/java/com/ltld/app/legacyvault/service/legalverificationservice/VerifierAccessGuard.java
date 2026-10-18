package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.exception.NotAssignedVerifierException;
import com.ltld.app.legacyvault.exception.VerificationRequestNotFoundException;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VerifierAccessGuard {

    private final LegalVerificationRequestRepository requestRepository;

    /**
     * Quy tắc quyền đọc hồ sơ của Verifier:
     * không tồn tại hoặc xung đột lợi ích (là Owner/Executor) -> 404;
     * PENDING -> verifier nào cũng xem được;
     * đã xử lý -> chỉ verifier được giao (403).
     */
    public LegalVerificationRequest requireReadable(UUID requestId, UUID verifierId) {
        LegalVerificationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new VerificationRequestNotFoundException("Không tìm thấy hồ sơ: " + requestId));

        boolean conflict = (request.getOwner() != null && verifierId.equals(request.getOwner().getId()))
                || (request.getExecutor() != null && verifierId.equals(request.getExecutor().getId()));
        if (conflict) {
            throw new VerificationRequestNotFoundException("Không tìm thấy hồ sơ: " + requestId);
        }

        if (request.getStatus() != LegalVerificationStatus.PENDING
                && (request.getVerifier() == null || !verifierId.equals(request.getVerifier().getId()))) {
            throw new NotAssignedVerifierException(
                    "Chỉ Legal Verifier đã xử lý hồ sơ mới được xem hoặc trả lời tin nhắn.");
        }
        return request;
    }
}