package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.AssignExecutorRequest;
import com.ltld.app.legacyvault.dto.executordto.ExecutorAssignmentResponse;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.service.executorservice.ExecutorAssignmentChangedEvent.ExecutorMail;
import com.ltld.app.legacyvault.exception.ExecutorManagementException;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.dmsservice.DmsActivationProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * FR-06: Owner chỉ định / thay thế / thu hồi Executor.
 *
 * <p>Mỗi Owner có tối đa một dòng PRIMARY và một dòng BACKUP (unique owner_id + executor_type);
 * thay người hay chỉ định lại sau khi thu hồi đều dùng lại đúng dòng đó.
 *
 * <p>Role EXECUTOR được thêm khi chỉ định và gỡ khi user không còn assignment nào chưa REVOKED.
 * Role chỉ là phân quyền thô: kiểm tra quyền truy cập thật phải dựa vào {@code ExecutorAssignment.status},
 * vì JWT cũ vẫn mang role EXECUTOR đến khi hết hạn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutorManagementServiceImpl implements ExecutorManagementService {

    static final String LAST_EXECUTOR_MESSAGE =
            "You cannot revoke your last executor while the Dead Man's Switch is active. Assign a replacement executor first.";

    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final DmsConfigRepository dmsConfigRepository;
    private final UserRepository userRepository;
    private final ExecutorRoleManager executorRoleManager;
    private final VaultRepository vaultRepository;
    private final AuditLogService auditLogService;
    // Mail gửi qua ExecutorAssignmentMailListener ở AFTER_COMMIT: rollback thì không gửi.
    private final ApplicationEventPublisher eventPublisher;

    // READ_COMMITTED: MySQL chạy REPEATABLE READ nên các câu đọc thường sau khi giữ khóa User vẫn thấy snapshot
    // cũ; READ_COMMITTED để existsBy... và lần nạp roles thấy dữ liệu đã commit mới nhất.
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExecutorAssignmentResponse assignExecutor(UUID ownerId, ExecutorType type, AssignExecutorRequest request) {
        ensureDmsNotTriggered(ownerId);
        User owner = loadOwner(ownerId);

        // 422 cho cả "chưa đăng ký" lẫn "chưa xác minh": request hợp lệ về cú pháp nhưng không xử lý được,
        // và dùng chung một thông báo cho chưa đăng ký và chưa xác minh.
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        User candidate = userRepository.findByEmail(email)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ExecutorManagementException(
                        "This email has no verified LegacyVault account. The executor must register and verify their account first.",
                        HttpStatus.UNPROCESSABLE_ENTITY));

        if (candidate.getId().equals(ownerId)) {
            throw new ExecutorManagementException("You cannot assign yourself as an executor.",
                    HttpStatus.BAD_REQUEST);
        }

        // Q26: người thụ hưởng (đã liên kết hoặc đang chờ đăng ký) của Owner không được đồng thời là Executor.
        // Đặt trước nhánh "cùng người" để cả trường hợp Executor hiện tại vừa trở thành người thụ hưởng cũng bị chặn.
        if (vaultRepository.existsBeneficiaryWithEmail(ownerId, email)) {
            throw new ExecutorManagementException(
                    "This person is your beneficiary and cannot also be your Digital Executor.",
                    HttpStatus.CONFLICT);
        }

        ExecutorAssignment current = executorAssignmentRepository
                .findByOwnerIdAndExecutorType(ownerId, type).orElse(null);
        boolean occupied = current != null && current.getStatus() != ExecutorStatus.REVOKED;

        // Chỉ định lại đúng người đang giữ vị trí: không đổi gì, không audit.
        if (occupied && current.getExecutorUser().getId().equals(candidate.getId())) {
            return toResponse(current);
        }

        ExecutorType otherType = type == ExecutorType.PRIMARY ? ExecutorType.BACKUP : ExecutorType.PRIMARY;
        boolean holdsOtherPosition = executorAssignmentRepository
                .findByOwnerIdAndExecutorType(ownerId, otherType)
                .filter(a -> a.getStatus() != ExecutorStatus.REVOKED)
                .filter(a -> a.getExecutorUser().getId().equals(candidate.getId()))
                .isPresent();
        if (holdsOtherPosition) {
            throw new ExecutorManagementException("This person is already your " + otherType
                    + " executor. The primary and backup executors must be different people.", HttpStatus.CONFLICT);
        }

        if (occupied && !request.isConfirmReplace()) {
            throw new ExecutorManagementException("The " + type + " executor position is already taken ("
                    + current.getExecutorUser().getEmail() + "). Resend with confirmReplace=true to replace them.",
                    HttpStatus.CONFLICT);
        }

        User previous = occupied ? current.getExecutorUser() : null;
        ExecutorAssignment assignment = current != null
                ? current
                : ExecutorAssignment.builder().owner(owner).executorType(type).build();
        assignment.setExecutorUser(candidate);
        assignment.setStatus(ExecutorStatus.INACTIVE);
        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setActivatedAt(null);
        assignment.setAcknowledgedAt(null);
        assignment.setRevokedAt(null);

        // Không đọc user.getRoles() trước dòng này: roles (LAZY) phải được nạp SAU khi giữ khóa User,
        // nếu không grant/revokeIfUnused sẽ quyết định trên dữ liệu cũ (findByIdForUpdate không làm mới entity đã nạp).
        lockUsersInOrder(candidate.getId(), previous != null ? previous.getId() : null);
        executorRoleManager.grant(candidate);
        if (previous != null) {
            executorRoleManager.revokeIfUnused(previous, assignment.getId());
        }

        ExecutorAssignment saved = saveAndFlush(assignment, type);

        String detail = "ownerId=" + ownerId + ", executorType=" + type
                + ", executorUserId=" + candidate.getId()
                + (previous != null ? ", previousExecutorUserId=" + previous.getId() : "");
        audit(previous != null ? AuditAction.EXECUTOR_REPLACED : AuditAction.EXECUTOR_ASSIGNED, owner, saved, detail);

        // FR-06 bước 5: người mới được báo chỉ định; khi thay người thì người cũ được báo bị thu hồi.
        List<ExecutorMail> mails = new ArrayList<>();
        mails.add(ExecutorMail.assigned(candidate.getEmail(), owner.getFullName(), owner.getEmail(), type));
        if (previous != null) {
            mails.add(ExecutorMail.revoked(previous.getEmail(), owner.getFullName(), owner.getEmail(), type));
        }
        eventPublisher.publishEvent(new ExecutorAssignmentChangedEvent(mails));

        return toResponse(saved);
    }

    // READ_COMMITTED: cùng lý do với assignExecutor; thêm nữa, FR-06 đọc lại DmsConfig sau khi khóa Executor
    // phải thấy dòng mà upsertConfig vừa commit.
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExecutorAssignmentResponse revokeExecutor(UUID ownerId, ExecutorType type) {
        Optional<DmsConfig> lockedDms = ensureDmsNotTriggered(ownerId);
        User owner = loadOwner(ownerId);

        ExecutorAssignment assignment = executorAssignmentRepository
                .findByOwnerIdAndExecutorType(ownerId, type)
                .filter(a -> a.getStatus() != ExecutorStatus.REVOKED)
                .orElseThrow(() -> new ExecutorManagementException(
                        "There is no " + type + " executor to revoke.", HttpStatus.NOT_FOUND));

        User executor = assignment.getExecutorUser();
        // Không đọc executor.getRoles() trước dòng này (xem comment ở assignExecutor).
        lockUsersInOrder(executor.getId());
        ensureNotLastExecutorWhileDmsActive(ownerId, assignment, lockedDms);

        assignment.setStatus(ExecutorStatus.REVOKED);
        assignment.setRevokedAt(LocalDateTime.now());
        executorRoleManager.revokeIfUnused(executor, assignment.getId());

        ExecutorAssignment saved = saveAndFlush(assignment, type);

        // reason=OWNER: phân biệt với reason=BR03_TIMEOUT khi FR-07 tự thu hồi Primary.
        audit(AuditAction.EXECUTOR_REVOKE, owner, saved, "ownerId=" + ownerId
                + ", executorType=" + type + ", executorUserId=" + executor.getId() + ", reason=OWNER");

        eventPublisher.publishEvent(new ExecutorAssignmentChangedEvent(List.of(
                ExecutorMail.revoked(executor.getEmail(), owner.getFullName(), owner.getEmail(), type))));

        return toResponse(saved);
    }

    // Chỉ đọc: không khóa DmsConfig, vẫn cho xem khi DMS đã TRIGGERED. Dòng REVOKED vẫn trả về
    // để Owner thấy vị trí đã bị thu hồi (bởi chính họ hoặc bởi FR-07 theo BR-03).
    @Override
    @Transactional(readOnly = true)
    public List<ExecutorAssignmentResponse> listExecutors(UUID ownerId) {
        return executorAssignmentRepository.findAllByOwnerId(ownerId).stream()
                .sorted(Comparator.comparing(ExecutorAssignment::getExecutorType))
                .map(ExecutorManagementServiceImpl::toResponse)
                .toList();
    }

    /**
     * FR-06 (2a): DMS đã kích hoạt bàn giao thì Owner không được thay đổi Executor.
     *
     * <p>Đọc DmsConfig với PESSIMISTIC_FORCE_INCREMENT (khóa dòng + tăng @Version ngay lúc đọc), nên xung đột
     * với trigger của FR-07 nổ ra ở bước này, TRƯỚC khi ghi audit:
     * <ul>
     *   <li>trigger đã ghi DmsConfig trước: lệnh đọc chờ trigger commit rồi thấy TRIGGERED -> 409;</li>
     *   <li>FR-06 khóa trước: trigger (đọc không khóa) bị lỗi @Version khi ghi DmsConfig và rollback,
     *       lượt quét sau sẽ thấy trạng thái mới.</li>
     * </ul>
     * Phải gọi ĐẦU TIÊN trong transaction: khóa DmsConfig trước, ExecutorAssignment sau, cùng thứ tự với
     * trigger của FR-07, để hai bên không khóa chéo nhau (deadlock). Chưa có DmsConfig thì không có gì để chặn.
     *
     * @return DmsConfig đã khóa, hoặc rỗng nếu Owner chưa có DmsConfig.
     */
    private Optional<DmsConfig> ensureDmsNotTriggered(UUID ownerId) {
        Optional<DmsConfig> config = dmsConfigRepository.findByOwnerIdForUpdate(ownerId);
        if (config.filter(c -> c.getStatus() == DmsStatus.TRIGGERED).isPresent()) {
            throw new ExecutorManagementException(
                    "The Dead Man's Switch has started the handover process; the executor cannot be changed.",
                    HttpStatus.CONFLICT);
        }
        return config;
    }

    /**
     * FR-06: DMS đang ACTIVE thì không được thu hồi Executor cuối cùng chưa REVOKED (thu hồi Primary khi còn
     * Backup vẫn được). Chưa có DMS hoặc DMS SUSPENDED thì cho phép; Admin mở lại DMS sẽ kiểm lại còn Executor
     * nhận bàn giao được.
     *
     * <p>Khóa FOR UPDATE các dòng Executor của Owner (sau khóa User, giữ thứ tự DmsConfig -> User -> ExecutorAssignment)
     * rồi mới đọc trạng thái DMS. Lúc ensureDmsNotTriggered chưa có DmsConfig thì không có khóa chung với
     * upsertConfig bật DMS lần đầu; upsertConfig cũng khóa các dòng này trước khi kiểm Primary, nên:
     * <ul>
     *   <li>upsert khóa trước: lệnh khóa ở đây chờ upsert commit, rồi đọc lại (READ_COMMITTED) thấy DMS ACTIVE -> 409;</li>
     *   <li>ở đây khóa trước: upsert chờ, sau đó thấy Primary đã REVOKED -> 409.</li>
     * </ul>
     * Đọc lại bằng đọc thường, không khóa DmsConfig sau ExecutorAssignment: khóa ngược thứ tự sẽ deadlock với trigger FR-07.
     */
    private void ensureNotLastExecutorWhileDmsActive(UUID ownerId, ExecutorAssignment target,
                                                     Optional<DmsConfig> lockedDms) {
        List<ExecutorAssignment> executors = executorAssignmentRepository.findAllByOwnerIdForUpdate(ownerId);
        boolean dmsActive = lockedDms.or(() -> dmsConfigRepository.findByOwnerId(ownerId))
                .filter(c -> c.getStatus() == DmsStatus.ACTIVE)
                .isPresent();
        boolean othersLeft = executors.stream()
                .anyMatch(a -> !a.getId().equals(target.getId()) && a.getStatus() != ExecutorStatus.REVOKED);
        if (dmsActive && !othersLeft) {
            throw new ExecutorManagementException(LAST_EXECUTOR_MESSAGE, HttpStatus.CONFLICT);
        }
    }

    private User loadOwner(UUID ownerId) {
        return userRepository.findById(ownerId)
                .orElseThrow(() -> new ExecutorManagementException("Account not found.", HttpStatus.NOT_FOUND));
    }

    /**
     * m1: khóa các User sắp được thêm/gỡ role EXECUTOR, theo id tăng dần (bỏ null), để hai Owner thao tác trên
     * cùng một người không ghi đè quyết định của nhau. Thứ tự khóa toàn cục:
     * DmsConfig (ensureDmsNotTriggered) -> User (ở đây) -> ExecutorAssignment (saveAndFlush).
     */
    private void lockUsersInOrder(UUID... userIds) {
        Arrays.stream(userIds)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(userRepository::findByIdForUpdate);
    }

    /**
     * Flush ngay để lỗi unique (hai request cùng tạo dòng cho một vị trí) và lỗi @Version của ExecutorAssignment
     * nổ ra TRƯỚC khi ghi audit (audit AFTER_COMPLETION + REQUIRES_NEW vẫn lưu khi rollback).
     */
    private ExecutorAssignment saveAndFlush(ExecutorAssignment assignment, ExecutorType type) {
        try {
            return executorAssignmentRepository.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException e) {
            log.warn("FR-06: concurrent write on executor position {} of owner {}: {}",
                    type, assignment.getOwner().getId(), e.getMessage());
            throw new ExecutorManagementException("The " + type
                    + " executor position was just updated by another request. Please reload and try again.", HttpStatus.CONFLICT);
        }
    }

    // vaultId = null: Executor gắn với Owner (BR-02), không với một Vault cụ thể.
    private void audit(AuditAction action, User owner, ExecutorAssignment assignment, String detail) {
        auditLogService.log(action, AuditResult.SUCCESS, owner.getId(), owner.getEmail(), null,
                DmsActivationProcessor.ENTITY_EXECUTOR_ASSIGNMENT, assignment.getId().toString(), detail);
    }

    private static ExecutorAssignmentResponse toResponse(ExecutorAssignment assignment) {
        User executor = assignment.getExecutorUser();
        return ExecutorAssignmentResponse.builder()
                .assignmentId(assignment.getId())
                .executorType(assignment.getExecutorType())
                .status(assignment.getStatus())
                .email(executor.getEmail())
                .fullName(executor.getFullName())
                .assignedAt(assignment.getAssignedAt() != null ? assignment.getAssignedAt() : assignment.getCreatedAt())
                .activatedAt(assignment.getActivatedAt())
                .acknowledgedAt(assignment.getAcknowledgedAt())
                .revokedAt(assignment.getRevokedAt())
                .build();
    }
}
