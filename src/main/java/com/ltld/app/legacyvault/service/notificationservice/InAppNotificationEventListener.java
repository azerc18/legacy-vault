package com.ltld.app.legacyvault.service.notificationservice;

import com.ltld.app.legacyvault.entity.DmsConfirmer;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsConfirmerStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.event.BeneficiaryReminderRequestedEvent;
import com.ltld.app.legacyvault.event.ExecutorTaskCompletedEvent;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.DmsConfirmerRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditEvent;
import com.ltld.app.legacyvault.service.auditservice.MailDataReader;
import com.ltld.app.legacyvault.service.executorservice.ExecutorAssignmentChangedEvent;
import com.ltld.app.legacyvault.service.executorservice.ExecutorAssignmentChangedEvent.ExecutorMail;
import com.ltld.app.legacyvault.service.executorservice.ExecutorAssignmentChangedEvent.MailType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ltld.app.legacyvault.service.notificationservice.InAppNotifier.REF_DMS_CONFIG;
import static com.ltld.app.legacyvault.service.notificationservice.InAppNotifier.REF_REQUEST;
import static com.ltld.app.legacyvault.service.notificationservice.InAppNotifier.REF_VAULT;
import static com.ltld.app.legacyvault.service.notificationservice.InAppNotifier.displayName;
import static com.ltld.app.legacyvault.service.notificationservice.InAppNotifier.draft;

/**
 * Chuông thông báo: ghi thông báo trong app từ các sự kiện đã có sẵn (audit và domain event), song song với
 * mail của AuditMailListener / ExecutorAssignmentMailListener. Không sửa nghiệp vụ gốc: chỉ nghe sau commit.
 *
 * <p>Chạy đồng bộ ở AFTER_COMMIT (ghi một dòng DB, không gọi SMTP) nên thông báo có ngay khi request trả về.
 * Mọi lỗi được {@link InAppNotifier#inNewTransaction} nuốt và log. Chỉ thêm case mới vào {@link #handle}.
 */
@Component
@RequiredArgsConstructor
public class InAppNotificationEventListener {

    private static final Pattern BENEFICIARY_ID = Pattern.compile("beneficiaryUserId=([0-9a-fA-F-]{36})");
    private static final Pattern PREVIOUS_ID = Pattern.compile("previousUserId=([0-9a-fA-F-]{36})");
    private static final Pattern OWNER_ID = Pattern.compile("ownerId=([0-9a-fA-F-]{36})");
    private static final Pattern CONFIRMER_ID = Pattern.compile("confirmerId=([0-9a-fA-F-]{36})");
    private static final Pattern DEADLINE_AT = Pattern.compile("deadlineAt=([^,\\s]+)");
    private static final Pattern POSTPONED_UNTIL = Pattern.compile("postponedUntil=([^,\\s]+)");
    private static final Pattern REASON = Pattern.compile("reason=(.+)$");

    private final InAppNotifier notifier;
    private final UserRepository userRepository;
    private final VaultRepository vaultRepository;
    private final VaultBeneficiaryRepository vaultBeneficiaryRepository;
    private final DmsConfirmerRepository dmsConfirmerRepository;
    private final MailDataReader mailData;
    private final DigitalAssetRepository digitalAssetRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAudit(AuditEvent event) {
        if (!handles(event)) {
            return;
        }
        notifier.inNewTransaction(event.action().name(), () -> handle(event));
    }

    // Lọc trước để các audit khác (đăng nhập, xem tài sản...) không mở giao dịch thừa
    private static boolean handles(AuditEvent e) {
        if (e == null || e.action() == null || !isSuccess(e)) {
            return false;
        }
        return switch (e.action()) {
            case BENEFICIARY_ASSIGNED, BENEFICIARY_REPLACED, BENEFICIARY_LINKED, DMS_CONFIRMATION_REQUESTED,
                 DMS_CONFIRMED_ALIVE, DMS_SUSPENDED, DMS_RESUMED, ASSET_VERIFICATION_VERIFIED, PAYMENT_SUCCEEDED,
                 VAULT_ARCHIVED, LEGAL_REQUEST_REOPENED -> true;
            default -> false;
        };
    }

    void handle(AuditEvent e) {
        String detail = detail(e);
        switch (e.action()) {
            // Owner chỉ định / thay người thụ hưởng: báo người mới (đã có tài khoản) và người bị thay
            case BENEFICIARY_ASSIGNED, BENEFICIARY_REPLACED -> {
                UUID beneficiaryId = uuid(BENEFICIARY_ID, detail);
                UUID previousId = uuid(PREVIOUS_ID, detail);
                vault(e).ifPresent(v -> {
                    String owner = displayName(v.getOwner());
                    if (beneficiaryId != null && !beneficiaryId.equals(previousId)) {
                        notifier.addForUser(beneficiaryId, beneficiaryAssigned(v, owner));
                    }
                    if (previousId != null && !previousId.equals(beneficiaryId)) {
                        notifier.addForUser(previousId, beneficiaryRemoved(v.getId(), v.getName(), owner));
                    }
                });
            }
            // Người được mời vừa xác minh email: lời mời chờ được gắn vào tài khoản (actor là chính họ)
            case BENEFICIARY_LINKED -> vault(e).ifPresent(v ->
                    notifier.addForUser(e.actorId(), beneficiaryAssigned(v, displayName(v.getOwner()))));
            // X2: quá hạn check-in, hệ thống hỏi người xác nhận tin cậy
            case DMS_CONFIRMATION_REQUESTED -> onConfirmationRequested(e, detail);
            // X2: một người xác nhận báo Owner vẫn ổn, lịch check-in được lùi
            case DMS_CONFIRMED_ALIVE -> {
                UUID confirmerId = uuid(CONFIRMER_ID, detail);
                String confirmer = confirmerId == null ? null
                        : dmsConfirmerRepository.findById(confirmerId).map(DmsConfirmer::getName).orElse(null);
                LocalDateTime until = dateTime(POSTPONED_UNTIL, detail);
                notifier.addForUser(uuid(OWNER_ID, detail), draft(NotificationType.DMS_OWNER_CONFIRMED_SAFE,
                        REF_DMS_CONFIG, e.entityId(),
                        "A trusted confirmer confirmed you are safe. Your check-in schedule was postponed.",
                        "confirmer", confirmer, "untilAt", until));
            }
            // Admin tạm dừng / mở lại Dead Man's Switch của Owner
            case DMS_SUSPENDED -> notifier.addForUser(uuid(OWNER_ID, detail), draft(NotificationType.DMS_PAUSED,
                    REF_DMS_CONFIG, e.entityId(), "An administrator paused your Dead Man's Switch.",
                    "reason", first(REASON, detail)));
            case DMS_RESUMED -> notifier.addForUser(uuid(OWNER_ID, detail), draft(NotificationType.DMS_RESUMED,
                    REF_DMS_CONFIG, e.entityId(), "An administrator resumed your Dead Man's Switch."));
            // Người nhận là Owner của tài sản (actor có thể là người thứ hai xác nhận giấy tờ, hoặc không có)
            case ASSET_VERIFICATION_VERIFIED -> {
                if (e.entityId() != null) {
                    digitalAssetRepository.findById(e.entityId()).ifPresent(asset -> {
                        Vault v = asset.getVault();
                        notifier.addForUser(v.getOwner().getId(), draft(NotificationType.ASSET_VERIFIED, REF_VAULT,
                                v.getId(), String.format("Your asset \"%s\" has been verified.", asset.getAssetName()),
                                "asset", asset.getAssetName(), "vault", v.getName()));
                    });
                }
            }
            case PAYMENT_SUCCEEDED -> notifier.addForUser(e.actorId(), draft(NotificationType.PAYMENT_SUCCEEDED,
                    null, null, "Payment successful. Your VIP plan is active."));
            // Vault lưu trữ vì quá hạn nhận: báo những người thụ hưởng còn hiệu lực
            case VAULT_ARCHIVED -> {
                if (e.vaultId() != null) {
                    mailData.vaultRecipients(e.vaultId()).ifPresent(v -> v.recipients().forEach(r ->
                            notifier.addForEmail(r.email(), draft(NotificationType.VAULT_ARCHIVED, REF_VAULT,
                                    e.vaultId(), String.format(
                                            "The vault \"%s\" was archived because the claim period expired.",
                                            v.vaultName()),
                                    "vault", v.vaultName()))));
                }
            }
            // Verifier mở lại hồ sơ: Executor cần bổ sung
            case LEGAL_REQUEST_REOPENED -> {
                if (e.entityId() != null) {
                    mailData.reopenedRequest(e.entityId()).ifPresent(r -> notifier.addForEmail(r.executorEmail(),
                            draft(NotificationType.VERIFICATION_REOPENED, REF_REQUEST, e.entityId(),
                                    String.format("The verification request for %s was reopened.", r.ownerLabel()),
                                    "owner", r.ownerLabel())));
                }
            }
            default -> {
                // các hành động khác không có thông báo trong app ở đây
            }
        }
    }

    private void onConfirmationRequested(AuditEvent e, String detail) {
        UUID ownerId = uuid(OWNER_ID, detail);
        Optional<User> owner = ownerId == null ? Optional.empty() : userRepository.findById(ownerId);
        if (owner.isEmpty() || e.entityId() == null) {
            return;
        }
        LocalDateTime deadline = dateTime(DEADLINE_AT, detail);
        String ownerName = displayName(owner.get());
        int asked = 0;
        for (DmsConfirmer c : dmsConfirmerRepository.findAllByDmsConfigIdOrderByCreatedAtAsc(e.entityId())) {
            if (c.getStatus() != DmsConfirmerStatus.ACTIVE) {
                continue;
            }
            asked++;
            // Link xác nhận chứa token chỉ có trong email, nên thông báo chỉ nhắc người xác nhận mở email
            if (c.getEmail() != null && !c.getEmail().equalsIgnoreCase(owner.get().getEmail())) {
                notifier.addForEmail(c.getEmail(), draft(NotificationType.DMS_CONFIRMATION_REQUESTED, null, null,
                        String.format("%s has missed their check-in. Please open the email we sent you to confirm "
                                + "whether they are safe.", ownerName),
                        "owner", ownerName, "deadlineAt", deadline));
            }
        }
        notifier.addForUser(ownerId, draft(NotificationType.DMS_CONFIRMATION_PENDING, REF_DMS_CONFIG, e.entityId(),
                "You missed your check-in. We are asking your trusted confirmers whether you are safe. "
                        + "Check in now to stop the handover.",
                "count", asked, "deadlineAt", deadline));
    }

    // FR-06: Owner chỉ định / thu hồi Executor, hệ thống thu hồi vì quá hạn, Admin thay người
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExecutorAssignmentChanged(ExecutorAssignmentChangedEvent event) {
        notifier.inNewTransaction("EXECUTOR_ASSIGNMENT", () -> {
            for (ExecutorMail mail : event.mails()) {
                String owner = mail.ownerName() == null || mail.ownerName().isBlank()
                        ? mail.ownerEmail() : mail.ownerName();
                String role = mail.executorType() == null ? null : mail.executorType().name();
                if (mail.type() == MailType.ASSIGNED) {
                    notifier.addForEmail(mail.recipientEmail(), draft(NotificationType.EXECUTOR_ASSIGNED, null, null,
                            String.format("%s appointed you as their executor.", owner),
                            "owner", owner, "role", role));
                } else {
                    // variant: FE chọn câu notifications.types.EXECUTOR_REVOKED_<variant>
                    String variant = switch (mail.type()) {
                        case REVOKED_TIMEOUT -> "TIMEOUT";
                        case REPLACED_BY_ADMIN -> "ADMIN";
                        default -> "OWNER";
                    };
                    notifier.addForEmail(mail.recipientEmail(), draft(NotificationType.EXECUTOR_REVOKED, null, null,
                            String.format("You are no longer an executor for %s.", owner),
                            "owner", owner, "role", role, "variant", variant));
                }
            }
        });
    }

    // N2-exec: Executor nhắc người thụ hưởng chưa phản hồi
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBeneficiaryReminder(BeneficiaryReminderRequestedEvent event) {
        notifier.inNewTransaction("CLAIM_DEADLINE_REMINDER", () ->
                vaultBeneficiaryRepository.findById(event.vaultBeneficiaryId()).ifPresent(vb ->
                        notifier.addForEmail(recipientEmail(vb), claimReminder(vb.getVault()))));
    }

    // N29: Executor báo xong một việc, người thụ hưởng vào xác nhận
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExecutorTaskCompleted(ExecutorTaskCompletedEvent event) {
        notifier.inNewTransaction("EXECUTOR_TASK_COMPLETED", () ->
                mailData.completedTask(event.taskId()).ifPresent(task -> task.recipients().forEach(r ->
                        notifier.addForEmail(r.email(), draft(NotificationType.EXECUTOR_TASK_COMPLETED, REF_VAULT,
                                event.vaultId(),
                                String.format("The executor completed a task for the vault \"%s\". "
                                        + "Please review and confirm the result.", task.vaultName()),
                                "vault", task.vaultName(), "platform", task.platform())))));
    }

    // ===== Câu thông báo dùng chung với các nơi gọi trực tiếp =====

    static InAppNotifier.Draft beneficiaryAssigned(Vault vault, String ownerName) {
        return draft(NotificationType.BENEFICIARY_ASSIGNED, REF_VAULT, vault.getId(),
                String.format("%s named you as a beneficiary of the vault \"%s\".", ownerName, vault.getName()),
                "owner", ownerName, "vault", vault.getName());
    }

    public static InAppNotifier.Draft beneficiaryRemoved(UUID vaultId, String vaultName, String ownerName) {
        // Không gắn link: người bị gỡ không còn quyền xem Vault
        return draft(NotificationType.BENEFICIARY_REMOVED, null, null,
                String.format("%s removed you as a beneficiary of the vault \"%s\".", ownerName, vaultName),
                "owner", ownerName, "vault", vaultName);
    }

    public static InAppNotifier.Draft claimReminder(Vault vault) {
        String owner = displayName(vault.getOwner());
        return draft(NotificationType.CLAIM_DEADLINE_REMINDER, REF_VAULT, vault.getId(),
                String.format("Reminder: please respond to your share of the vault \"%s\" of %s before the deadline.",
                        vault.getName(), owner),
                "owner", owner, "vault", vault.getName(), "deadlineAt", vault.getClaimDeadlineAt());
    }

    private static String recipientEmail(VaultBeneficiary vb) {
        return vb.getUser() != null ? vb.getUser().getEmail() : vb.getEmail();
    }

    private Optional<Vault> vault(AuditEvent e) {
        return e.vaultId() == null ? Optional.empty() : vaultRepository.findById(e.vaultId());
    }

    private static boolean isSuccess(AuditEvent e) {
        Map<String, Object> m = e.metadata();
        return m != null && AuditResult.SUCCESS.name().equals(Objects.toString(m.get("result"), null));
    }

    private static String detail(AuditEvent e) {
        Object d = e.metadata() == null ? null : e.metadata().get("detail");
        return d == null ? null : d.toString();
    }

    static String first(Pattern pattern, String text) {
        if (text == null) {
            return null;
        }
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).strip() : null;
    }

    private static UUID uuid(Pattern pattern, String text) {
        String value = first(pattern, text);
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static LocalDateTime dateTime(Pattern pattern, String text) {
        String value = first(pattern, text);
        try {
            return value == null ? null : LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
