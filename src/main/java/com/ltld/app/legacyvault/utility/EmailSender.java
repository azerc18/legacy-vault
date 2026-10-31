package com.ltld.app.legacyvault.utility;

import com.ltld.app.legacyvault.enums.ExecutorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
     * F2: nhắc Owner check-in Dead Man's Switch kèm nút/liên kết một chạm ("Tôi vẫn an toàn / I'm OK").
     */
    public void sendDmsCheckinReminder(String email, LocalDateTime checkinDueAt, LocalDateTime triggerAt,
                                       int reminderNumber, int reminderCount, String oneClickCheckinUrl) {
        String linkSection = (oneClickCheckinUrl != null && !oneClickCheckinUrl.isBlank())
                ? "\n\nOne-click check-in (Tôi vẫn an toàn / I'm OK): " + oneClickCheckinUrl
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