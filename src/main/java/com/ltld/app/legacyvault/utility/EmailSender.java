package com.ltld.app.legacyvault.utility;

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
                "Mã xác thực OTP - LegacyVault",
                "Mã OTP của bạn là: " + otp);
    }

    @Async
    public void sendPasswordResetEmail(String email, String otp) {
        send(email,
                "Đặt lại mật khẩu - LegacyVault",
                "Mã đặt lại mật khẩu của bạn là: " + otp
                        + "\nNếu bạn không yêu cầu, hãy bỏ qua email này và cân nhắc đổi mật khẩu.");
    }

    // FR-02: mời người thụ hưởng chưa có tài khoản (hoặc chưa xác minh email) đăng ký
    @Async
    public void sendBeneficiaryInvitation(String email, String ownerName, String vaultName) {
        String link = registerLink();
        sendWithoutOtpFooter(email,
                "Lời mời trở thành người thụ hưởng - LegacyVault",
                ownerName + " đã chỉ định bạn là người thụ hưởng của Vault \"" + vaultName + "\" trên LegacyVault."
                        + "\nVui lòng đăng ký tài khoản (hoặc hoàn tất xác minh email nếu đã đăng ký) bằng chính địa chỉ email này"
                        + " để được liên kết với Vault."
                        + (link != null ? "\nĐăng ký tại: " + link : ""));
    }

    // FR-02: thông báo cho người thụ hưởng đã có tài khoản
    @Async
    public void sendBeneficiaryAssignedNotice(String email, String ownerName, String vaultName) {
        sendWithoutOtpFooter(email,
                "Bạn được chỉ định là người thụ hưởng - LegacyVault",
                ownerName + " đã chỉ định bạn là người thụ hưởng của Vault \"" + vaultName + "\" trên LegacyVault."
                        + "\nBạn sẽ nhận được thông báo khi Vault được mở khóa để bàn giao.");
    }

    // FR-02 (bước 7): xác nhận cho Owner
    @Async
    public void sendBeneficiaryAssignmentConfirmation(String ownerEmail, String vaultName,
                                                      String beneficiaryEmail, boolean pendingRegistration) {
        sendWithoutOtpFooter(ownerEmail,
                "Xác nhận chỉ định người thụ hưởng - LegacyVault",
                "Bạn đã chỉ định " + beneficiaryEmail + " là người thụ hưởng của Vault \"" + vaultName + "\"."
                        + (pendingRegistration
                        ? "\nNgười này chưa có tài khoản đã xác minh; hệ thống đã gửi lời mời đăng ký."
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
                .append("Xin chào ").append(fullName == null ? "" : fullName).append(",\n\n")
                .append("Hồ sơ \"").append(vaultName).append("\" của ").append(ownerName)
                .append(" đã được mở khóa và bàn giao cho bạn trên LegacyVault.\n\n");

        if (!accountActive) {
            body.append("Tài khoản của bạn chưa được kích hoạt. ")
                    .append("Vui lòng hoàn tất đăng ký và xác minh email trước, sau đó đăng nhập để nhận tài sản.\n");
            String registerLink = registerLink();
            if (registerLink != null) {
                body.append("Đăng ký tại: ").append(registerLink).append("\n");
            }
            body.append("\n");
        }

        String link = vaultLink(vaultId);
        if (link != null) {
            body.append("Truy cập hồ sơ: ").append(link).append("\n\n");
        }

        body.append("Cách nhận tài sản:\n")
                .append("1. Đăng nhập LegacyVault bằng tài khoản Beneficiary.\n")
                .append("2. Chọn hồ sơ được bàn giao và xác thực danh tính (mã OTP qua email hoặc eKYC).\n")
                .append("3. Xem hoặc tải xuống tài sản, sau đó xác nhận đã nhận bàn giao.\n\n")
                .append("Hạn nhận tài sản: ").append(deadline).append(" (GMT+7).\n")
                .append("Sau thời hạn này, hồ sơ sẽ bị khóa lưu trữ và bạn không thể truy cập nội dung tài sản.\n\n")
                .append("Vui lòng không chia sẻ email này với bất kỳ ai.");

        sendWithoutOtpFooter(email, "Tài sản số được bàn giao cho bạn - LegacyVault", body.toString());
    }

    public void sendVerificationRequestNotification(String email, UUID requestId) {
        sendWithoutOtpFooter(email,
                "Hồ sơ xác minh mới - LegacyVault",
                "Có hồ sơ xác minh mới cần xử lý. Mã hồ sơ: " + requestId
                        + "\nVui lòng đăng nhập để xem chi tiết.");
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
        sendWithoutOtpFooter(email,
                "Nhắc check-in Dead Man's Switch (" + reminderNumber + "/" + reminderCount + ") - LegacyVault",
                "Bạn chưa check-in Dead Man's Switch trên LegacyVault."
                        + "\nHạn check-in: " + formatDeadline(checkinDueAt) + " (giờ Việt Nam)"
                        + "\nNếu bạn không phản hồi, hệ thống sẽ kích hoạt quy trình bàn giao vào lúc "
                        + formatDeadline(triggerAt) + " (giờ Việt Nam) và gửi thông báo cho Digital Executor của bạn."
                        + "\n\nĐể check-in, hãy đăng nhập LegacyVault và chọn \"Check-in\" trong mục Cài đặt Dead Man's Switch."
                        + "\nĐây là lần nhắc thứ " + reminderNumber + " trên tổng số " + reminderCount + " lần.");
    }

    public void sendVerificationMessageNotification(String email, UUID requestId) {
        sendWithoutOtpFooter(email,
                "Tin nhắn mới trong hồ sơ xác minh - LegacyVault",
                "Có tin nhắn hoặc tệp bổ sung mới trong hồ sơ xác minh. Mã hồ sơ: " + requestId
                        + "\nVui lòng đăng nhập để xem chi tiết.");
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body
                + "\nMã có hiệu lực trong " + (OTP_EXPIRE_SECONDS / 60) + " phút."
                + "\nVui lòng không chia sẻ mã này với bất kỳ ai.");
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