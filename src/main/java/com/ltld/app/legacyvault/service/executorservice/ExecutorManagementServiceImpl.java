package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.AssignExecutorRequest;
import com.ltld.app.legacyvault.dto.executordto.ExecutorAssignmentResponse;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.event.ExecutorAssignmentChangedEvent;
import com.ltld.app.legacyvault.event.ExecutorAssignmentChangedEvent.ExecutorMail;
import com.ltld.app.legacyvault.exception.ExecutorManagementException;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.RoleRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.dmsservice.DmsActivationProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

    static final String ROLE_EXECUTOR = "EXECUTOR";

    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final DmsConfigRepository dmsConfigRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditLogService auditLogService;
    // Mail gửi qua ExecutorAssignmentMailListener ở AFTER_COMMIT: rollback thì không gửi.
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ExecutorAssignmentResponse assignExecutor(UUID ownerId, ExecutorType type, AssignExecutorRequest request) {
        ensureDmsNotTriggered(ownerId);
        User owner = loadOwner(ownerId);

        // 422 cho cả "chưa đăng ký" lẫn "chưa xác minh": request hợp lệ về cú pháp nhưng không xử lý được,
        // và dùng chung một thông báo để không lộ email nào đã có tài khoản.
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        User candidate = userRepository.findByEmail(email)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ExecutorManagementException(
                        "Email này chưa có tài khoản LegacyVault đã xác minh. Executor phải đăng ký và xác minh tài khoản trước.",
                        HttpStatus.UNPROCESSABLE_ENTITY));

        if (candidate.getId().equals(ownerId)) {
            throw new ExecutorManagementException("Không thể tự chỉ định chính mình làm Executor.",
                    HttpStatus.BAD_REQUEST);
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
            throw new ExecutorManagementException("Người này đang là Executor " + otherType
                    + " của bạn. Executor chính và dự phòng phải là hai người khác nhau.", HttpStatus.CONFLICT);
        }

        if (occupied && !request.isConfirmReplace()) {
            throw new ExecutorManagementException("Vị trí Executor " + type + " đã có người ("
                    + current.getExecutorUser().getEmail() + "). Gửi lại với confirmReplace=true để thay thế.",
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

        grantExecutorRole(candidate);
        if (previous != null) {
            revokeExecutorRoleIfUnused(previous, assignment.getId());
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

    @Override
    @Transactional
    public ExecutorAssignmentResponse revokeExecutor(UUID ownerId, ExecutorType type) {
        ensureDmsNotTriggered(ownerId);
        User owner = loadOwner(ownerId);

        ExecutorAssignment assignment = executorAssignmentRepository
                .findByOwnerIdAndExecutorType(ownerId, type)
                .filter(a -> a.getStatus() != ExecutorStatus.REVOKED)
                .orElseThrow(() -> new ExecutorManagementException(
                        "Không có Executor " + type + " nào để thu hồi.", HttpStatus.NOT_FOUND));

        assignment.setStatus(ExecutorStatus.REVOKED);
        assignment.setRevokedAt(LocalDateTime.now());
        User executor = assignment.getExecutorUser();
        revokeExecutorRoleIfUnused(executor, assignment.getId());

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
     */
    private void ensureDmsNotTriggered(UUID ownerId) {
        dmsConfigRepository.findByOwnerIdForUpdate(ownerId)
                .filter(c -> c.getStatus() == DmsStatus.TRIGGERED)
                .ifPresent(c -> {
                    throw new ExecutorManagementException(
                            "Dead Man's Switch đã kích hoạt quy trình bàn giao, không thể thay đổi Executor.",
                            HttpStatus.CONFLICT);
                });
    }

    private User loadOwner(UUID ownerId) {
        return userRepository.findById(ownerId)
                .orElseThrow(() -> new ExecutorManagementException("Không tìm thấy tài khoản.", HttpStatus.NOT_FOUND));
    }

    private void grantExecutorRole(User user) {
        boolean hasRole = user.getRoles().stream().anyMatch(r -> ROLE_EXECUTOR.equals(r.getCode()));
        if (!hasRole) {
            Role role = roleRepository.findByCode(ROLE_EXECUTOR)
                    .orElseThrow(() -> new IllegalStateException("Role not found: " + ROLE_EXECUTOR));
            user.getRoles().add(role);
        }
    }

    // assignmentId là dòng vừa bị thu hồi/thay người, loại ra vì trạng thái mới của nó chưa được flush.
    private void revokeExecutorRoleIfUnused(User user, UUID assignmentId) {
        boolean stillAssigned = executorAssignmentRepository
                .existsByExecutorUserIdAndStatusNotAndIdNot(user.getId(), ExecutorStatus.REVOKED, assignmentId);
        if (!stillAssigned) {
            user.getRoles().removeIf(r -> ROLE_EXECUTOR.equals(r.getCode()));
        }
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
            throw new ExecutorManagementException("Vị trí Executor " + type
                    + " vừa được cập nhật bởi một yêu cầu khác. Vui lòng tải lại và thử lại.", HttpStatus.CONFLICT);
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
                .revokedAt(assignment.getRevokedAt())
                .build();
    }
}
