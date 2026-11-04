package com.ltld.app.legacyvault.utility;

import com.ltld.app.legacyvault.enums.ExecutorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EmailSender {
    private final JavaMailSender javaMailSender;
    private final int OTP_EXPIRE_SECONDS = 300;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    @Value("${app.frontend.base-url:}")
    private String frontendBaseUrl;

    @Async
    public void sendEmail(String email, String otp) {
        send(email,
                "OTP verification code - LegacyVault",
                "Your OTP code is: " + otp);
    }

    @Async
    public void sendPasswordResetEmail(String email, String otp) {
        send(email,
                "Reset your password - LegacyVault",
                "Your password reset code is: " + otp
                        + "\nIf you did not request this, ignore this email and consider changing your password.");
    }

    // FR-02: mời người thụ hưởng chưa có tài khoản (hoặc chưa xác minh email) đăng ký
    @Async
    public void sendBeneficiaryInvitation(String email, String ownerName, String vaultName) {
        String link = registerLink();
        sendWithoutOtpFooter(email,
                "Invitation to become a beneficiary - LegacyVault",
                ownerName + " has named you the beneficiary of vault \"" + vaultName + "\" on LegacyVault."
                        + "\nPlease register an account (or finish verifying your email if you have already registered) using this email address"
                        + " so you can be linked to the vault."
                        + (link != null ? "\nRegister at: " + link : ""));
    }

    // FR-02: thông báo cho người thụ hưởng đã có tài khoản
    @Async
    public void sendBeneficiaryAssignedNotice(String email, String ownerName, String vaultName) {
        sendWithoutOtpFooter(email,
                "You have been named a beneficiary - LegacyVault",
                ownerName + " has named you the beneficiary of vault \"" + vaultName + "\" on LegacyVault."
                        + "\nYou will be notified when the vault is unlocked for handover.");
    }

    // N2-owner: báo cho người bị gỡ khỏi vault
    @Async
    public void sendBeneficiaryRemovedNotice(String email, String ownerName, String vaultName) {
        sendWithoutOtpFooter(email,
                "You are no longer a beneficiary - LegacyVault",
                ownerName + " has removed you as a beneficiary of vault \"" + vaultName + "\" on LegacyVault.");
    }

    /**
     * N2-notify: báo cho người nhận thay khi một phần tài sản được chuyển sang họ
     * (người chính từ chối hoặc không phản hồi kịp). Không nêu danh tính người kia.
     */
    @Async
    public void sendAssetsReassignedNotice(String email, String fullName, String ownerName, String vaultName,
                                           int assetCount, boolean noResponse, UUID vaultId) {
        StringBuilder body = new StringBuilder()
                .append("Hello ").append(fullName == null ? "" : fullName).append(",\n\n")
                .append(assetCount).append(" asset(s) in the vault \"").append(vaultName).append("\" of ")
                .append(ownerName).append(" have been reassigned to you because another beneficiary ")
                .append(noResponse ? "did not respond in time" : "declined their share").append(".\n\n");
        String link = vaultLink(vaultId);
        if (link != null) {
            body.append("Open the vault: ").append(link).append("\n\n");
        }
        body.append("Please sign in, verify your identity, then view and confirm receipt of the assets allocated to you.");
        sendWithoutOtpFooter(email, "Assets reassigned to you - LegacyVault", body.toString());
    }

    // FR-02 (bước 7): xác nhận cho Owner
    @Async
    public void sendBeneficiaryAssignmentConfirmation(String ownerEmail, String vaultName,
                                                      String beneficiaryEmail, boolean pendingRegistration) {
        sendWithoutOtpFooter(ownerEmail,
                "Beneficiary assignment confirmed - LegacyVault",
                "You have named " + beneficiaryEmail + " as the beneficiary of vault \"" + vaultName + "\"."
                        + (pendingRegistration
                        ? "\nThis person does not have a verified account yet; a sign-up invitation has been sent."
                        : ""));
    }

    /** Hạn nhận theo giờ Việt Nam: claimDeadlineAt là LocalDateTime theo múi giờ máy chủ nên đổi sang GMT+7. */
    public static String formatDeadline(LocalDateTime claimDeadlineAt) {
        return claimDeadlineAt.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(VN_ZONE)
                .format(DEADLINE_FORMAT);
    }

    /**
     * FR-16: thông báo cho Beneficiary khi Vault được mở khóa.
     * Cố ý KHÔNG dùng @Async: người gọi cần biết gửi thành công hay không (lỗi gửi ném MailException)
     * để chỉ ghi notifiedAt khi gửi được và để lượt quét sau gửi lại.
     * accountActive = false: tài khoản chưa kích hoạt (SRS FR-16 nhánh 2a) nên kèm lời nhắc đăng ký/xác minh.
     * vaultId: dùng để tạo link mở đúng hồ sơ; không cấu hình frontend thì bỏ dòng link.
     */
    public void sendVaultUnlockedNotification(String email, String fullName, String ownerName, String vaultName,
                                              LocalDateTime claimDeadlineAt, boolean accountActive, UUID vaultId) {
        String deadline = formatDeadline(claimDeadlineAt);

        StringBuilder body = new StringBuilder()
                .append("Hello ").append(fullName == null ? "" : fullName).append(",\n\n")
                .append("The vault \"").append(vaultName).append("\" of ").append(ownerName)
                .append(" has been unlocked and handed over to you on LegacyVault.\n\n");

        if (!accountActive) {
            body.append("Your account has not been activated yet. ")
                    .append("Please complete registration and verify your email first, then sign in to claim the assets.\n");
            String registerLink = registerLink();
            if (registerLink != null) {
                body.append("Register at: ").append(registerLink).append("\n");
            }
            body.append("\n");
        }

        String link = vaultLink(vaultId);
        if (link != null) {
            body.append("Open the vault: ").append(link).append("\n\n");
        }

        body.append("How to claim the assets:\n")
                .append("1. Sign in to LegacyVault with your beneficiary account.\n")
                .append("2. Open the handed-over vault and verify your identity (email OTP or eKYC).\n")
                .append("3. View or download the assets, then confirm you have received the handover.\n\n")
                .append("Claim deadline: ").append(deadline).append(" (GMT+7).\n")
                .append("After this deadline the vault will be archived and you will no longer be able to access the asset contents.\n\n")
                .append("Please do not share this email with anyone.");

        sendWithoutOtpFooter(email, "Digital assets handed over to you - LegacyVault", body.toString());
    }

    // N2-exec: Executor nhắc người thụ hưởng chưa phản hồi việc nhận tài sản
    @Async
    public void sendBeneficiaryClaimReminder(String email, String fullName, String ownerName, String vaultName,
                                             LocalDateTime claimDeadlineAt, UUID vaultId) {
        StringBuilder body = new StringBuilder()
                .append("Hello ").append(fullName == null ? "" : fullName).append(",\n\n")
                .append("This is a reminder from the executor of ").append(ownerName)
                .append(": the vault \"").append(vaultName)
                .append("\" has been handed over to you and is still waiting for your response on LegacyVault.\n\n");
        String link = vaultLink(vaultId);
        if (link != null) {
            body.append("Open the vault: ").append(link).append("\n\n");
        }
        body.append("Please sign in, verify your identity, then accept or decline the assets allocated to you.\n")
                .append("Claim deadline: ").append(formatDeadline(claimDeadlineAt)).append(" (GMT+7).\n\n")
                .append("Please do not share this email with anyone.");

        sendWithoutOtpFooter(email, "Reminder: assets waiting for you - LegacyVault", body.toString());
    }

    public void sendVerificationRequestNotification(String email, UUID requestId) {
        sendWithoutOtpFooter(email,
                "New verification request - LegacyVault",
                "A new verification request needs review. Request ID: " + requestId
                        + "\nPlease sign in to view the details.");
    }

    private String frontendBase() {
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            return null;
        }
        return frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
    }

    private String vaultLink(UUID vaultId) {
        String base = frontendBase();
        return (vaultId == null || base == null) ? null : base + "/beneficiary/vaults/" + vaultId;
    }

    private String registerLink() {
        String base = frontendBase();
        return base == null ? null : base + "/register";
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
     * F2: nhắc Owner check-in kèm link một chạm tới trang FE /dms/checkin; mở link chỉ hiện trang,
     * Owner bấm nút "I'm OK" thì mới check-in. checkinToken null/rỗng -> mail như cũ, không kèm link.
     */
    public void sendDmsCheckinReminder(String email, LocalDateTime checkinDueAt, LocalDateTime triggerAt,
                                       int reminderNumber, int reminderCount, String checkinToken) {
        String base = frontendBase();
        String linkSection = (base != null && checkinToken != null && !checkinToken.isBlank())
                ? "\n\nStill here? Open this link and press \"I'm OK\" to check in without signing in: "
                        + base + "/dms/checkin?token=" + URLEncoder.encode(checkinToken, StandardCharsets.UTF_8)
                : "";
        sendWithoutOtpFooter(email,
                "Dead Man's Switch check-in reminder (" + reminderNumber + "/" + reminderCount + ") - LegacyVault",
                "You have not checked in to your Dead Man's Switch on LegacyVault."
                        + "\nCheck-in deadline: " + formatDeadline(checkinDueAt) + " (Vietnam time)"
                        + "\nIf you do not respond, the system will start the handover process at "
                        + formatDeadline(triggerAt) + " (Vietnam time) and notify your Digital Executor."
                        + linkSection
                        + "\n\nTo check in, sign in to LegacyVault and choose \"Check-in\" in the Dead Man's Switch settings."
                        + "\nThis is reminder " + reminderNumber + " of " + reminderCount + ".");
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
        String base = frontendBase();
        if (base == null) {
            throw new IllegalStateException("app.frontend.base-url is not configured; cannot build the confirmation link");
        }
        String confirmUrl = base + "/dms/confirm?token=" + URLEncoder.encode(confirmationToken, StandardCharsets.UTF_8);
        String ownerText = ownerLabel(ownerName, ownerEmail);
        String body = "Hello " + (confirmerName == null ? "" : confirmerName) + ",\n\n"
                + ownerText + " designated you as a Trusted Social Confirmer on LegacyVault.\n"
                + "Their Dead Man's Switch check-in and grace period have expired without response.\n\n"
                + "Before the system notifies their Digital Executor, please open the link below and tell us whether "
                + "they are safe, before " + formatDeadline(deadlineAt) + " (Vietnam time):\n\n"
                + confirmUrl + "\n\n"
                + "Opening the link does not change anything; your answer is only recorded when you press a button on that page.\n"
                + "If no confirmer responds within 72 hours, the system will automatically proceed with the handover.";
        sendWithoutOtpFooter(
                confirmerEmail,
                "URGENT: Please confirm safety status for " + ownerDisplayName(ownerName, ownerEmail) + " - LegacyVault",
                body
        );
    }

    /**
     * X2 (m5): báo Owner rằng người xác nhận chéo đang được liên hệ; Owner check-in trước hạn là huỷ quy trình.
     */
    public void sendOwnerConfirmationPendingNotice(String ownerEmail, String ownerName, List<String> confirmerNames,
                                                   LocalDateTime deadlineAt) {
        String base = frontendBase();
        String dmsLink = base == null ? null : base + "/owner/dms";
        String names = confirmerNames == null || confirmerNames.isEmpty()
                ? "your trusted confirmers" : String.join(", ", confirmerNames);
        sendWithoutOtpFooter(ownerEmail,
                "Your trusted confirmers are being contacted - LegacyVault",
                "Hello " + ownerDisplayName(ownerName, ownerEmail) + ",\n\n"
                        + "Your Dead Man's Switch check-in and grace period have expired.\n"
                        + "Your trusted confirmers (" + names + ") are being contacted to confirm whether you are safe.\n"
                        + "Check in before " + formatDeadline(deadlineAt)
                        + " (Vietnam time) to cancel this and return your Dead Man's Switch to normal."
                        + (dmsLink != null ? "\nCheck in: " + dmsLink : ""));
    }

    /**
     * X2: thông báo cho Owner biết người xác nhận chéo vừa báo "Owner vẫn bình an" và hoãn DMS 7 ngày.
     */
    public void sendOwnerConfirmedSafeNotice(String ownerEmail, String confirmerName, LocalDateTime postponedUntil) {
        String body = "Your Trusted Social Confirmer (" + (confirmerName == null ? "Trusted Contact" : confirmerName)
                + ") confirmed that you are safe.\n"
                + "Your Dead Man's Switch activation has been postponed until "
                + formatDeadline(postponedUntil) + " (Vietnam time).\n"
                + "Please sign in to LegacyVault and check in as soon as possible.";
        sendWithoutOtpFooter(
                ownerEmail,
                "Dead Man's Switch postponed by Trusted Confirmer - LegacyVault",
                body
        );
    }

    public void sendVerificationMessageNotification(String email, UUID requestId) {
        sendWithoutOtpFooter(email,
                "New message in a verification request - LegacyVault",
                "There is a new message or supporting file in a verification request. Request ID: " + requestId
                        + "\nPlease sign in to view the details.");
    }

    // FR-06: method đồng bộ; bản thân listener AFTER_COMMIT đã chạy @Async, lỗi do listener bắt và log.
    // Không nêu tên Executor còn lại của Owner.
    public void sendExecutorAssignedNotice(String to, String ownerName, String ownerEmail, ExecutorType type) {
        String role = type == ExecutorType.PRIMARY ? "Primary executor (PRIMARY)" : "Backup executor (BACKUP)";
        sendWithoutOtpFooter(to,
                "You have been assigned as a Digital Executor - LegacyVault",
                ownerLabel(ownerName, ownerEmail) + " has assigned you as " + role + " on LegacyVault."
                        + "\nYou do not need to do anything now. When the Dead Man's Switch of " + ownerDisplayName(ownerName, ownerEmail)
                        + " is triggered, the system will notify you to sign in and accept the handover process."
                        + "\nIf you do not know this person, please contact LegacyVault support.");
    }

    public void sendExecutorRevokedNotice(String to, String ownerName, String ownerEmail, ExecutorType type) {
        String role = type == ExecutorType.PRIMARY ? "Primary executor (PRIMARY)" : "Backup executor (BACKUP)";
        sendWithoutOtpFooter(to,
                "Your Digital Executor role has been revoked - LegacyVault",
                ownerLabel(ownerName, ownerEmail) + " has revoked your " + role + " role on LegacyVault."
                        + "\nFrom now on you can no longer carry out the handover process for "
                        + ownerDisplayName(ownerName, ownerEmail) + ".");
    }

    // FR-07 / BR-03: hệ thống tự thu hồi vì Primary không xác nhận trong hạn, Owner không làm gì (thường đã mất)
    public void sendExecutorTimeoutRevokedNotice(String to, String ownerName, String ownerEmail, int waitDays) {
        sendWithoutOtpFooter(to,
                "Your Digital Executor role has ended - LegacyVault",
                "You did not confirm the handover for " + ownerLabel(ownerName, ownerEmail)
                        + " within " + waitDays + " days, so the role was passed to the backup executor."
                        + "\nYou can no longer carry out the handover process for "
                        + ownerDisplayName(ownerName, ownerEmail) + ".");
    }

    // FR-07: Admin chỉ định Executor mới để tiếp tục bàn giao (người cũ bỏ dở hoặc không còn dùng được)
    public void sendExecutorReplacedByAdminNotice(String to, String ownerName, String ownerEmail,
                                                  ExecutorType type) {
        String role = type == ExecutorType.PRIMARY ? "Primary executor (PRIMARY)" : "Backup executor (BACKUP)";
        sendWithoutOtpFooter(to,
                "Your Digital Executor role has ended - LegacyVault",
                "An administrator has assigned a new " + role + " for " + ownerLabel(ownerName, ownerEmail)
                        + " to continue the handover process."
                        + "\nYou can no longer carry out the handover process for "
                        + ownerDisplayName(ownerName, ownerEmail) + ".");
    }

    // T1: Nhắc Owner trước 7 ngày khi gói VIP sắp hết hạn
    public void sendSubscriptionExpiryReminder(String to, String ownerName, String planName, LocalDateTime expiresAt) {
        String base = frontendBase();
        String billingLink = base == null ? null : base + "/billing";
        sendWithoutOtpFooter(to,
                "Your VIP subscription is expiring soon - LegacyVault",
                "Hello " + ownerDisplayName(ownerName, to) + ",\n\n"
                        + "Your \"" + planName + "\" subscription on LegacyVault will expire on "
                        + formatDeadline(expiresAt) + " (GMT+7).\n"
                        + "Even if your subscription expires, your existing vaults, Dead Man's Switch, and handover process will continue to work normally.\n"
                        + "Renew your plan to keep creating new vaults and adding more beneficiaries."
                        + (billingLink != null ? "\nManage billing: " + billingLink : ""));
    }

    // FR-06: Owner tạo trước khi có fullName (null/rỗng) thì hiển thị email, không in chữ "null".
    private static String ownerDisplayName(String ownerName, String ownerEmail) {
        return ownerName == null || ownerName.isBlank() ? ownerEmail : ownerName;
    }

    // "Tên (email)"; không có tên thì chỉ "email".
    private static String ownerLabel(String ownerName, String ownerEmail) {
        return ownerName == null || ownerName.isBlank() ? ownerEmail : ownerName + " (" + ownerEmail + ")";
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body
                + "\nThe code is valid for " + (OTP_EXPIRE_SECONDS / 60) + " minutes."
                + "\nPlease do not share this code with anyone.");
        javaMailSender.send(message);
    }

    // Gửi email thường, không gắn câu về thời hạn OTP như send()
    private void sendWithoutOtpFooter(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        javaMailSender.send(message);
    }
}