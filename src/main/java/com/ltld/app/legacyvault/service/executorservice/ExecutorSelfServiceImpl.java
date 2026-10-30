package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.MyExecutorAssignmentResponse;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.repository.VerificationExchangeMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExecutorSelfServiceImpl implements ExecutorSelfService {

    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final VaultRepository vaultRepository;
    private final LegalVerificationRequestRepository legalVerificationRequestRepository;
    private final VerificationExchangeMessageRepository verificationExchangeMessageRepository;
    private final DmsConfigRepository dmsConfigRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MyExecutorAssignmentResponse> listMyAssignments(String executorLogin) {
        if (executorLogin == null || executorLogin.isBlank()) {
            return List.of();
        }
        // Principal là user id (JWT subject) hoặc email, giống ExecutorAccessGuard.
        List<ExecutorAssignment> assignments = parseUuid(executorLogin)
                .map(executorAssignmentRepository::findLiveByExecutorUserId)
                .orElseGet(() -> executorAssignmentRepository
                        .findLiveByExecutorEmail(executorLogin.trim().toLowerCase(Locale.ROOT)));
        if (assignments.isEmpty()) {
            return List.of();
        }

        // B8: số liệu dashboard cho mọi Owner trong vài truy vấn gộp, để FE không phải gọi thêm từng Owner (N+1).
        List<UUID> ownerIds = assignments.stream().map(a -> a.getOwner().getId()).distinct().toList();
        Map<UUID, Long> vaultCountByOwner = toCountMap(vaultRepository.countByOwnerIds(ownerIds));
        Map<UUID, Integer> waitDaysByOwner = new HashMap<>();
        for (DmsConfig config : dmsConfigRepository.findAllByOwnerIdIn(ownerIds)) {
            waitDaysByOwner.put(config.getOwner().getId(), config.getBackupActivationWaitDays());
        }

        // Cả danh sách cùng một Executor; hồ sơ mới nhất trước nên phần tử đầu mỗi Owner là hồ sơ mới nhất.
        UUID executorId = assignments.getFirst().getExecutorUser().getId();
        List<LegalVerificationRequest> requests =
                legalVerificationRequestRepository.findByExecutorIdOrderByCreatedAtDesc(executorId);
        Map<UUID, LegalVerificationRequest> latestByOwner = new HashMap<>();
        Map<UUID, UUID> ownerByRequest = new HashMap<>();
        for (LegalVerificationRequest r : requests) {
            latestByOwner.putIfAbsent(r.getOwner().getId(), r);
            ownerByRequest.put(r.getId(), r.getOwner().getId());
        }
        Map<UUID, Long> unreadByOwner = new HashMap<>();
        if (!requests.isEmpty()) {
            for (Object[] row : verificationExchangeMessageRepository.countUnreadForExecutor(ownerByRequest.keySet())) {
                unreadByOwner.merge(ownerByRequest.get((UUID) row[0]), (Long) row[1], Long::sum);
            }
        }

        return assignments.stream().map(a -> {
            UUID ownerId = a.getOwner().getId();
            LegalVerificationRequest latest = latestByOwner.get(ownerId);
            return toResponse(a,
                    vaultCountByOwner.getOrDefault(ownerId, 0L),
                    latest == null ? null : latest.getStatus(),
                    unreadByOwner.getOrDefault(ownerId, 0L),
                    acknowledgeDeadline(a, waitDaysByOwner.get(ownerId)));
        }).toList();
    }

    // B8: hạn xác nhận chỉ có nghĩa khi đã kích hoạt mà chưa xác nhận; tính như BR-03 (kích hoạt + số ngày chờ).
    private static LocalDateTime acknowledgeDeadline(ExecutorAssignment a, Integer waitDays) {
        if (a.getStatus() != ExecutorStatus.ACTIVE || a.getAcknowledgedAt() != null
                || a.getActivatedAt() == null || waitDays == null) {
            return null;
        }
        return a.getActivatedAt().plusDays(waitDays);
    }

    private static Map<UUID, Long> toCountMap(List<Object[]> rows) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static java.util.Optional<UUID> parseUuid(String value) {
        try {
            return java.util.Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    private static MyExecutorAssignmentResponse toResponse(ExecutorAssignment a, long vaultCount,
                                                           LegalVerificationStatus latestRequestStatus,
                                                           long unreadMessageCount,
                                                           LocalDateTime acknowledgeDeadlineAt) {
        return MyExecutorAssignmentResponse.builder()
                .assignmentId(a.getId())
                .ownerFullName(a.getOwner().getFullName())
                .ownerEmail(a.getOwner().getEmail())
                .executorType(a.getExecutorType())
                .status(a.getStatus())
                .assignedAt(a.getAssignedAt())
                .activatedAt(a.getActivatedAt())
                .acknowledgedAt(a.getAcknowledgedAt())
                .vaultCount(vaultCount)
                .latestRequestStatus(latestRequestStatus)
                .unreadMessageCount(unreadMessageCount)
                .acknowledgeDeadlineAt(acknowledgeDeadlineAt)
                .build();
    }
}
