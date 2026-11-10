package com.ltld.app.legacyvault.service.notificationservice;

import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Chuông thông báo: nơi duy nhất ghi thông báo trong app (channel IN_APP) cho các sự kiện trước đây chỉ gửi email.
 *
 * <p>Quy ước: thông báo là phần phụ, không bao giờ được làm hỏng nghiệp vụ chính. {@link #toUser} / {@link #toEmail}
 * tự mở giao dịch riêng (REQUIRES_NEW, vì thường được gọi ở AFTER_COMMIT khi giao dịch gốc đã đóng) và nuốt mọi
 * RuntimeException (chỉ log warn). Người nhận theo email mà chưa có tài khoản thì bỏ qua: họ vẫn nhận được email.
 */
@Slf4j
@Component
public class InAppNotifier {

    public static final String REF_VAULT = "Vault";
    public static final String REF_REQUEST = "LegalVerificationRequest";
    public static final String REF_DMS_CONFIG = "DmsConfig";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate requiresNewTx;

    public InAppNotifier(NotificationRepository notificationRepository, UserRepository userRepository,
                         PlatformTransactionManager transactionManager) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Một thông báo cần ghi. content là câu tiếng Anh dự phòng; params để FE dịch theo type. */
    public record Draft(NotificationType type, String referenceType, UUID referenceId, String content,
                        Map<String, String> params) {
    }

    public static Draft draft(NotificationType type, String referenceType, UUID referenceId, String content,
                              Object... params) {
        return new Draft(type, referenceType, referenceId, content, NotificationParams.of(params));
    }

    /** Ghi cho một tài khoản, trong giao dịch riêng; lỗi chỉ log. */
    public void toUser(UUID userId, Draft draft) {
        if (userId == null) {
            return;
        }
        inNewTransaction(draft.type().name(), () -> addForUser(userId, draft));
    }

    /** Ghi cho tài khoản có email này (nếu có), trong giao dịch riêng; lỗi chỉ log. */
    public void toEmail(String email, Draft draft) {
        if (email == null || email.isBlank()) {
            return;
        }
        inNewTransaction(draft.type().name(), () -> addForEmail(email, draft));
    }

    /**
     * Như {@link #toEmail} nhưng bỏ qua nếu thông báo mới nhất cùng type + tham chiếu của người này có cùng tham số.
     * Dùng cho nơi có thể chạy lại (job gửi lại khi SMTP lỗi) để không ghi trùng.
     */
    public void toEmailOnce(String email, Draft draft) {
        if (email == null || email.isBlank()) {
            return;
        }
        inNewTransaction(draft.type().name(), () -> findByEmail(email).ifPresent(user -> {
            String params = NotificationParams.toJson(draft.params());
            boolean duplicate = notificationRepository
                    .findFirstByRecipientIdAndTypeAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                            user.getId(), draft.type(), draft.referenceType(), draft.referenceId())
                    .filter(n -> n.getChannel() == NotificationChannel.IN_APP)
                    .map(n -> Objects.equals(n.getParams(), params))
                    .orElse(false);
            if (!duplicate) {
                save(user, draft);
            }
        }));
    }

    /**
     * Chạy work trong một giao dịch REQUIRES_NEW và nuốt RuntimeException. Dùng cho listener cần đọc dữ liệu
     * (lazy) rồi ghi nhiều thông báo bằng {@link #addForUser} / {@link #addForEmail} trong cùng giao dịch.
     */
    public void inNewTransaction(String what, Runnable work) {
        try {
            requiresNewTx.executeWithoutResult(status -> work.run());
        } catch (RuntimeException e) {
            log.warn("In-app notification {} not saved: {}", what, e.getClass().getSimpleName(), e);
        }
    }

    /** Chỉ gọi bên trong {@link #inNewTransaction}. */
    public void addForUser(UUID userId, Draft draft) {
        if (userId != null) {
            userRepository.findById(userId).ifPresent(user -> save(user, draft));
        }
    }

    /** Chỉ gọi bên trong {@link #inNewTransaction}. */
    public void addForEmail(String email, Draft draft) {
        findByEmail(email).ifPresent(user -> save(user, draft));
    }

    private Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String trimmed = email.trim();
        Optional<User> user = userRepository.findByEmail(trimmed);
        // Email tài khoản lưu dạng chữ thường; email người thụ hưởng / người xác nhận có thể được nhập khác hoa thường
        return user.isPresent() ? user : userRepository.findByEmail(trimmed.toLowerCase(Locale.ROOT));
    }

    private void save(User recipient, Draft draft) {
        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .type(draft.type())
                .channel(NotificationChannel.IN_APP)
                .referenceType(draft.referenceType())
                .referenceId(draft.referenceId())
                .content(draft.content())
                .params(NotificationParams.toJson(draft.params()))
                .status(NotificationStatus.SENT)
                .sentAt(LocalDateTime.now())
                .build());
    }

    /** Tên hiển thị: họ tên, thiếu thì email. */
    public static String displayName(User user) {
        if (user == null) {
            return "";
        }
        return user.getFullName() == null || user.getFullName().isBlank() ? user.getEmail() : user.getFullName();
    }
}
