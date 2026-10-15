package com.ltld.app.legacyvault.utility;

import com.ltld.app.legacyvault.enums.ExecutorType;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class EmailSender {
    private final JavaMailSender javaMailSender;
    private final int OTP_EXPIRE_SECONDS = 300;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
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
     */
    public void sendVaultUnlockedNotification(String email, String fullName, String ownerName, String vaultName,
                                              LocalDateTime claimDeadlineAt, boolean accountActive) {
        String deadline = formatDeadline(claimDeadlineAt);

        StringBuilder body = new StringBuilder()
                .append("Xin chào ").append(fullName == null ? "" : fullName).append(",\n\n")
                .append("Hồ sơ \"").append(vaultName).append("\" của ").append(ownerName)
                .append(" đã được mở khóa và bàn giao cho bạn trên LegacyVault.\n\n");

        if (!accountActive) {
            body.append("Tài khoản của bạn chưa được kích hoạt. ")
                    .append("Vui lòng hoàn tất đăng ký và xác minh email trước, sau đó đăng nhập để nhận tài sản.\n\n");
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

    // FR-06: method đồng bộ; bản thân listener AFTER_COMMIT đã chạy @Async, lỗi do listener bắt và log.
    // Không nêu tên Executor còn lại của Owner.
    public void sendExecutorAssignedNotice(String to, String ownerName, String ownerEmail, ExecutorType type) {
        String role = type == ExecutorType.PRIMARY ? "Executor chính (PRIMARY)" : "Executor dự phòng (BACKUP)";
        sendWithoutOtpFooter(to,
                "Bạn được chỉ định làm Digital Executor - LegacyVault",
                ownerLabel(ownerName, ownerEmail) + " đã chỉ định bạn làm " + role + " trên LegacyVault."
                        + "\nBạn chưa cần làm gì lúc này. Khi Dead Man's Switch của " + ownerDisplayName(ownerName, ownerEmail)
                        + " được kích hoạt, hệ thống sẽ gửi thông báo để bạn đăng nhập và xác nhận tiếp nhận quy trình bàn giao."
                        + "\nNếu bạn không biết người này, hãy liên hệ bộ phận hỗ trợ LegacyVault.");
    }

    public void sendExecutorRevokedNotice(String to, String ownerName, String ownerEmail, ExecutorType type) {
        String role = type == ExecutorType.PRIMARY ? "Executor chính (PRIMARY)" : "Executor dự phòng (BACKUP)";
        sendWithoutOtpFooter(to,
                "Quyền Digital Executor của bạn đã bị thu hồi - LegacyVault",
                ownerLabel(ownerName, ownerEmail) + " đã thu hồi vai trò " + role + " của bạn trên LegacyVault."
                        + "\nTừ thời điểm này bạn không còn quyền thực hiện quy trình bàn giao cho "
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