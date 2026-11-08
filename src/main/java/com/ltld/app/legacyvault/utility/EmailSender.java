package com.ltld.app.legacyvault.utility;

import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.utility.mail.MailCatalog;
import com.ltld.app.legacyvault.utility.mail.MailContent;
import com.ltld.app.legacyvault.utility.mail.MailLayout;
import com.ltld.app.legacyvault.utility.mail.MailText;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Gửi email của hệ thống. Mỗi mail có bản HTML (khung chung trong utility.mail.MailLayout) kèm bản text thường.
 * Nội dung từng mail nằm ở MailCatalog, chữ ở resources/mail/mail*.properties (Việt mặc định, Anh ở mail_en).
 * Chữ ký 22 hàm public giữ nguyên nên không phải sửa listener / service nào.
 */
@Component
@RequiredArgsConstructor
public class EmailSender {
    private static final String DEFAULT_LOGO = "https://legacyvault.codes/brand/logo-mark.png";

    private final JavaMailSender javaMailSender;
    @Value("${app.frontend.base-url:}")
    private String frontendBaseUrl;
    // Logo phải là URL tuyệt đối (Gmail chặn ảnh nhúng)
    @Value("${app.mail.logo-url:" + DEFAULT_LOGO + "}")
    private String logoUrl;
    // Địa chỉ gửi; để trống thì dùng tài khoản SMTP. Tên hiển thị luôn là "LegacyVault".
    @Value("${app.mail.from:${spring.mail.username:}}")
    private String fromAddress;
    // vi | en. Khi PR #195 (preferredLanguage) merge, localeFor() sẽ tra theo người nhận; đây là giá trị dự phòng.
    @Value("${app.mail.default-language:vi}")
    private String defaultLanguage;

    @Async
    public void sendEmail(String email, String otp) {
        send(email, catalog(email).otp(otp));
    }

    @Async
    public void sendPasswordResetEmail(String email, String otp) {
        send(email, catalog(email).passwordReset(otp));
    }

    // FR-02: mời người thụ hưởng chưa có tài khoản (hoặc chưa xác minh email) đăng ký
    @Async
    public void sendBeneficiaryInvitation(String email, String ownerName, String vaultName) {
        send(email, catalog(email).beneficiaryInvitation(ownerName, vaultName));
    }

    // FR-02: thông báo cho người thụ hưởng đã có tài khoản
    @Async
    public void sendBeneficiaryAssignedNotice(String email, String ownerName, String vaultName) {
        send(email, catalog(email).beneficiaryAssigned(ownerName, vaultName));
    }

    // N2-owner: báo cho người bị gỡ khỏi vault
    @Async
    public void sendBeneficiaryRemovedNotice(String email, String ownerName, String vaultName) {
        send(email, catalog(email).beneficiaryRemoved(ownerName, vaultName));
    }

    /**
     * N2-notify: báo cho người nhận thay khi một phần tài sản được chuyển sang họ
     * (người chính từ chối hoặc không phản hồi kịp). Không nêu danh tính người kia.
     */
    @Async
    public void sendAssetsReassignedNotice(String email, String fullName, String ownerName, String vaultName,
                                           int assetCount, boolean noResponse, UUID vaultId) {
        send(email, catalog(email).assetsReassigned(fullName, ownerName, vaultName, assetCount, noResponse, vaultId));
    }

    // FR-02 (bước 7): xác nhận cho Owner
    @Async
    public void sendBeneficiaryAssignmentConfirmation(String ownerEmail, String vaultName,
                                                      String beneficiaryEmail, boolean pendingRegistration) {
        send(ownerEmail, catalog(ownerEmail).beneficiaryAssignmentConfirmation(vaultName, beneficiaryEmail, pendingRegistration));
    }

    /** Hạn nhận theo giờ Việt Nam: claimDeadlineAt là LocalDateTime theo múi giờ máy chủ nên đổi sang GMT+7. */
    public static String formatDeadline(LocalDateTime claimDeadlineAt) {
        return MailCatalog.formatDeadline(claimDeadlineAt);
    }

    /**
     * FR-16: thông báo cho Beneficiary khi Vault được mở khóa.
     * Cố ý KHÔNG dùng @Async: người gọi cần biết gửi thành công hay không (lỗi gửi ném MailException)
     * để chỉ ghi notifiedAt khi gửi được và để lượt quét sau gửi lại.
     * accountActive = false: tài khoản chưa kích hoạt (SRS FR-16 nhánh 2a) nên kèm lời nhắc đăng ký/xác minh.
     * vaultId: dùng để tạo link mở đúng hồ sơ; không cấu hình frontend thì bỏ nút / link.
     */
    public void sendVaultUnlockedNotification(String email, String fullName, String ownerName, String vaultName,
                                              LocalDateTime claimDeadlineAt, boolean accountActive, UUID vaultId) {
        send(email, catalog(email).vaultUnlocked(fullName, ownerName, vaultName, claimDeadlineAt, accountActive, vaultId));
    }

    // N2-exec: Executor nhắc người thụ hưởng chưa phản hồi việc nhận tài sản
    @Async
    public void sendBeneficiaryClaimReminder(String email, String fullName, String ownerName, String vaultName,
                                             LocalDateTime claimDeadlineAt, UUID vaultId) {
        send(email, catalog(email).beneficiaryClaimReminder(fullName, ownerName, vaultName, claimDeadlineAt, vaultId));
    }

    // F4b: thông báo khi lời nhắn di nguyện / Time-Capsule đến hạn mở cho người thụ hưởng
    // (n1: không để lộ tiêu đề riêng tư trong mail; m4: bổ sung link đăng ký nếu người nhận chưa có tài khoản)
    @Async
    public void sendLegacyMessageDeliveredNotification(String email, String fullName, String ownerName,
                                                       String vaultName, UUID vaultId, boolean hasAccount) {
        send(email, catalog(email).legacyMessageDelivered(fullName, ownerName, vaultName, vaultId, hasAccount));
    }

    public void sendVerificationRequestNotification(String email, UUID requestId) {
        send(email, catalog(email).verificationRequest(requestId));
    }

    /**
     * FR-03: nhắc Owner check-in Dead Man's Switch. Không @Async: người gọi gửi sau khi transaction đã commit
     * và cần biết gửi lỗi để trả lại lần nhắc. Giờ hiển thị đổi sang giờ Việt Nam qua formatDeadline.
     */
    public void sendDmsCheckinReminder(String email, LocalDateTime checkinDueAt, LocalDateTime triggerAt,
                                       int reminderNumber, int reminderCount) {
        sendDmsCheckinReminder(email, checkinDueAt, triggerAt, reminderNumber, reminderCount, null);
    }

    /**
     * F2: nhắc Owner check-in kèm nút một chạm tới trang FE /dms/checkin; mở link chỉ hiện trang,
     * Owner bấm "I'm OK" thì mới check-in. checkinToken null/rỗng -> không kèm nút.
     */
    public void sendDmsCheckinReminder(String email, LocalDateTime checkinDueAt, LocalDateTime triggerAt,
                                       int reminderNumber, int reminderCount, String checkinToken) {
        send(email, catalog(email).dmsCheckinReminder(checkinDueAt, triggerAt, reminderNumber, reminderCount, checkinToken));
    }

    /**
     * X2 (C1): gửi email khẩn cấp cho Trusted Social Confirmer khi Owner quá hạn check-in + gracePeriodDays.
     * Chỉ MỘT link tới trang FE công khai /dms/confirm?token=...; mở link không đổi gì (trình quét link của hộp thư
     * có thể tự mở). Quyết định chỉ được ghi khi người nhận bấm nút trên trang (POST /api/dms/confirm).
     * Không dựng được link (chưa cấu hình app.frontend.base-url) thì ném lỗi để người gọi tính là gửi thất bại.
     */
    public void sendDmsCrossConfirmationRequest(
            String confirmerEmail,
            String confirmerName,
            String ownerName,
            String ownerEmail,
            LocalDateTime deadlineAt,
            String confirmationToken
    ) {
        send(confirmerEmail, catalog(confirmerEmail)
                .dmsCrossConfirmationRequest(confirmerName, ownerName, ownerEmail, deadlineAt, confirmationToken));
    }

    /** X2 (m5): báo Owner rằng người xác nhận chéo đang được liên hệ; Owner check-in trước hạn là huỷ quy trình. */
    public void sendOwnerConfirmationPendingNotice(String ownerEmail, String ownerName, List<String> confirmerNames,
                                                   LocalDateTime deadlineAt) {
        send(ownerEmail, catalog(ownerEmail).ownerConfirmationPending(ownerName, ownerEmail, confirmerNames, deadlineAt));
    }

    /** X2: thông báo cho Owner biết người xác nhận chéo vừa báo "Owner vẫn bình an" và hoãn DMS 7 ngày. */
    public void sendOwnerConfirmedSafeNotice(String ownerEmail, String confirmerName, LocalDateTime postponedUntil) {
        send(ownerEmail, catalog(ownerEmail).ownerConfirmedSafe(confirmerName, postponedUntil));
    }

    public void sendVerificationMessageNotification(String email, UUID requestId) {
        send(email, catalog(email).verificationMessage(requestId));
    }

    // FR-06: method đồng bộ; bản thân listener AFTER_COMMIT đã chạy @Async, lỗi do listener bắt và log.
    // Không nêu tên Executor còn lại của Owner.
    public void sendExecutorAssignedNotice(String to, String ownerName, String ownerEmail, ExecutorType type) {
        send(to, catalog(to).executorAssigned(ownerName, ownerEmail, type));
    }

    public void sendExecutorRevokedNotice(String to, String ownerName, String ownerEmail, ExecutorType type) {
        send(to, catalog(to).executorRevoked(ownerName, ownerEmail, type));
    }

    // FR-07 / BR-03: hệ thống tự thu hồi vì Primary không xác nhận trong hạn, Owner không làm gì (thường đã mất)
    public void sendExecutorTimeoutRevokedNotice(String to, String ownerName, String ownerEmail, int waitDays) {
        send(to, catalog(to).executorTimeoutRevoked(ownerName, ownerEmail, waitDays));
    }

    // FR-07: Admin chỉ định Executor mới để tiếp tục bàn giao (người cũ bỏ dở hoặc không còn dùng được)
    public void sendExecutorReplacedByAdminNotice(String to, String ownerName, String ownerEmail,
                                                  ExecutorType type) {
        send(to, catalog(to).executorReplacedByAdmin(ownerName, ownerEmail, type));
    }

    // T1: Nhắc Owner trước 7 ngày khi gói VIP sắp hết hạn
    public void sendSubscriptionExpiryReminder(String to, String ownerName, String planName, LocalDateTime expiresAt) {
        send(to, catalog(to).subscriptionExpiry(to, ownerName, planName, expiresAt));
    }

    /* ======================= Nội bộ ======================= */

    /**
     * Ngôn ngữ của người nhận. Hiện dùng app.mail.default-language (mặc định vi).
     * Sau khi PR #195 merge: inject UserLanguageResolver và trả về resolver.forEmail(to).toLocale().
     */
    Locale localeFor(String to) {
        return "en".equalsIgnoreCase(defaultLanguage) ? MailText.EN : MailText.VI;
    }

    private MailCatalog catalog(String to) {
        return new MailCatalog(new MailText(localeFor(to)), frontendBaseUrl);
    }

    private void send(String to, MailContent content) {
        MailCatalog brandSource = catalog(to);
        String site = frontendBaseUrl == null || frontendBaseUrl.isBlank() ? null : frontendBaseUrl;
        MailLayout.Rendered mail = MailLayout.render(content, brandSource.text(),
                new MailLayout.Brand(logoUrl == null || logoUrl.isBlank() ? DEFAULT_LOGO : logoUrl, site));

        MimeMessage message = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(mail.subject());
            helper.setText(mail.plain(), mail.html());   // multipart/alternative: text thường + HTML
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(fromAddress, "LegacyVault");
            }
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new MailPreparationException("Could not build email: " + mail.subject(), e);
        }
        javaMailSender.send(message);
    }
}
