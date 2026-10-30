package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.ExecutorVerificationRequestResponse;
import com.ltld.app.legacyvault.dto.executordto.VaultHandoverProgressResponse;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VerificationRequestVault;
import com.ltld.app.legacyvault.enums.HandoverStatus;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.VerificationExchangeMessageRepository;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExecutorHandoverServiceImpl implements ExecutorHandoverService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ExecutorAccessGuard executorAccessGuard;
    private final VerificationRequestVaultRepository verificationRequestVaultRepository;
    private final LegalVerificationRequestRepository legalVerificationRequestRepository;
    private final VerificationExchangeMessageRepository verificationExchangeMessageRepository;

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

    @Override
    @Transactional(readOnly = true)
    public Page<ExecutorVerificationRequestResponse> listVerificationRequests(UUID assignmentId, String executorLogin,
                                                                              int page, int size) {
        ExecutorAssignment assignment = executorAccessGuard.requireActiveAssignment(assignmentId, executorLogin);

        Page<LegalVerificationRequest> requests = legalVerificationRequestRepository.findByExecutorIdAndOwnerId(
                assignment.getExecutorUser().getId(), assignment.getOwner().getId(),
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        List<UUID> ids = requests.map(LegalVerificationRequest::getId).getContent();
        if (ids.isEmpty()) {
            return requests.map(r -> toResponse(r, List.of(), 0));
        }

        // Một truy vấn cho Vault và một cho số tin chưa đọc của cả trang, không N+1.
        Map<UUID, List<ExecutorVerificationRequestResponse.VaultRef>> vaultsByRequest =
                verificationRequestVaultRepository.findByRequestIdIn(ids).stream()
                        .collect(Collectors.groupingBy(rv -> rv.getRequest().getId(), Collectors.mapping(
                                rv -> new ExecutorVerificationRequestResponse.VaultRef(
                                        rv.getVault().getId(), rv.getVault().getName()),
                                Collectors.toList())));
        Map<UUID, Long> unreadByRequest = new HashMap<>();
        for (Object[] row : verificationExchangeMessageRepository.countUnreadForExecutor(ids)) {
            unreadByRequest.put((UUID) row[0], (Long) row[1]);
        }

        return requests.map(r -> toResponse(r, vaultsByRequest.getOrDefault(r.getId(), List.of()),
                unreadByRequest.getOrDefault(r.getId(), 0L)));
    }

    private static ExecutorVerificationRequestResponse toResponse(
            LegalVerificationRequest request, List<ExecutorVerificationRequestResponse.VaultRef> vaults, long unread) {
        return ExecutorVerificationRequestResponse.builder()
                .id(request.getId())
                .status(request.getStatus())
                .vaults(vaults)
                .rejectionReason(request.getRejectionReason())
                .createdAt(request.getCreatedAt())
                .decidedAt(request.getDecidedAt())
                .unreadMessageCount(unread)
                .build();
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
