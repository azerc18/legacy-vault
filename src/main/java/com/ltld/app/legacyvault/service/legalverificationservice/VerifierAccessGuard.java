package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.exception.NotAssignedVerifierException;
import com.ltld.app.legacyvault.exception.VerificationRequestNotFoundException;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VerifierAccessGuard {

    private final LegalVerificationRequestRepository requestRepository;
    private final VaultBeneficiaryRepository beneficiaryRepository;

    /**
     * Quy tắc quyền đọc hồ sơ của Verifier:
     * không tồn tại hoặc xung đột lợi ích (là Owner/Executor) -> 404;
     * PENDING -> verifier nào cũng xem được;
     * đã xử lý -> chỉ verifier được giao (403).
     */
    public LegalVerificationRequest requireReadable(UUID requestId, UUID verifierId) {
        LegalVerificationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new VerificationRequestNotFoundException("Request not found: " + requestId));

        if (isConflict(request, verifierId) || hasBeneficiaryConflict(requestId, verifierId)) {
            throw new VerificationRequestNotFoundException("Request not found: " + requestId);
        }

        if (request.getStatus() != LegalVerificationStatus.PENDING
                && (request.getVerifier() == null || !verifierId.equals(request.getVerifier().getId()))) {
            throw new NotAssignedVerifierException(
                    "Only the Legal Verifier who handled this request can access it.");
        }
        return request;
    }

    /** N2: Verifier là beneficiary của bất kỳ vault nào trong hồ sơ (xung đột lợi ích). */
    public boolean hasBeneficiaryConflict(UUID requestId, UUID verifierId) {
        return beneficiaryRepository.isBeneficiaryOfRequest(requestId, verifierId);
    }

    /** Verifier là Owner hoặc Executor của hồ sơ (xung đột lợi ích). */
    public static boolean isConflict(LegalVerificationRequest request, UUID verifierId) {
        return (request.getOwner() != null && verifierId.equals(request.getOwner().getId()))
                || (request.getExecutor() != null && verifierId.equals(request.getExecutor().getId()));
    }
}
