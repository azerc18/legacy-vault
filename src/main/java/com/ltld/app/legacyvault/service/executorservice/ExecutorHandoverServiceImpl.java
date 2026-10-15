package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.VaultHandoverProgressResponse;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VerificationRequestVault;
import com.ltld.app.legacyvault.enums.HandoverStatus;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExecutorHandoverServiceImpl implements ExecutorHandoverService {

    private final ExecutorAccessGuard executorAccessGuard;
    private final VerificationRequestVaultRepository verificationRequestVaultRepository;

    @Override
    @Transactional(readOnly = true)
    public List<VaultHandoverProgressResponse> getHandoverProgress(UUID assignmentId, String executorLogin) {
        ExecutorAssignment assignment = executorAccessGuard.requireActiveAssignment(assignmentId, executorLogin);

        List<VerificationRequestVault> rows = verificationRequestVaultRepository.findProgressRows(
                assignment.getExecutorUser().getId(), assignment.getOwner().getId());

        Map<UUID, VerificationRequestVault> latestPerVault = new LinkedHashMap<>();
        for (VerificationRequestVault row : rows) {
            latestPerVault.putIfAbsent(row.getVault().getId(), row);
        }

        return latestPerVault.values().stream().map(ExecutorHandoverServiceImpl::toResponse).toList();
    }

    private static VaultHandoverProgressResponse toResponse(VerificationRequestVault row) {
        Vault vault = row.getVault();
        LegalVerificationRequest request = row.getRequest();
        return VaultHandoverProgressResponse.builder()
                .vaultId(vault.getId())
                .vaultName(vault.getName())
                .handoverStatus(statusOf(vault, request))
                .requestId(request.getId())
                .requestedAt(request.getCreatedAt())
                .decidedAt(request.getDecidedAt())
                .rejectionReason(request.getRejectionReason())
                .unlockedAt(vault.getUnlockedAt())
                .claimDeadlineAt(vault.getClaimDeadlineAt())
                .build();
    }

    private static HandoverStatus statusOf(Vault vault, LegalVerificationRequest request) {
        return switch (vault.getStatus()) {
            case ARCHIVED_LOCKED -> HandoverStatus.ARCHIVED_LOCKED;
            case CLAIMED -> HandoverStatus.CLAIMED;
            case UNLOCKED -> HandoverStatus.UNLOCKED;
            case ACTIVE -> switch (request.getStatus()) {
                case PENDING -> HandoverStatus.PENDING;
                case APPROVED, SIGNED -> HandoverStatus.VERIFIED;
                case REJECTED -> HandoverStatus.REJECTED;
            };
        };
    }
}
