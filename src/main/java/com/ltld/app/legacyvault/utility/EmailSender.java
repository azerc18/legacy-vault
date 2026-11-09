package com.ltld.app.legacyvault.utility;

import com.ltld.app.legacyvault.enums.AppLanguage;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.service.userservice.UserLanguageResolver;
import com.ltld.app.legacyvault.utility.MailHtml.Style;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EmailSender {
    private final JavaMailSender javaMailSender;
    private final UserLanguageResolver languageResolver;
    private final int OTP_EXPIRE_SECONDS = 300;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    @Value("${app.frontend.base-url:}")
    private String frontendBaseUrl;
    /** Địa chỉ người gửi hiển thị, ví dụ "LegacyVault <no-reply@legacyvault.codes>". Trống thì dùng tài khoản SMTP. */
    @Value("${app.mail.from:}")
    private String mailFrom;

    @Async
    public void sendEmail(String email, String otp) {
        AppLanguage lang = languageResolver.forEmail(email);
        sendCode(email, lang, Style.CODE, "mail.otp", otp, List.of(), MailMessages.get(lang, "mail.codeFooter", OTP_EXPIRE_SECONDS / 60));
    }

    @Async
    public void sendAssetVerificationOtpEmail(String email, String otp, int validMinutes) {
        AppLanguage lang = languageResolver.forEmail(email);
        sendCode(email, lang, Style.CODE, "mail.assetOtp", otp, List.of(), MailMessages.get(lang, "mail.assetOtp.footer", validMinutes));
    }

    @Async
    public void sendPasswordResetEmail(String email, String otp) {
        AppLanguage lang = languageResolver.forEmail(email);
        sendCode(email, lang, Style.SECURITY, "mail.reset", otp,
                List.of(MailMessages.get(lang, "mail.reset.warn")),
                MailMessages.get(lang, "mail.codeFooter", OTP_EXPIRE_SECONDS / 60));
    }

    // N1: chào mừng sau khi xác minh email
    @Async
    public void sendWelcomeEmail(String email, String fullName) {
        AppLanguage lang = languageResolver.forEmail(email);
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.greeting", displayName(fullName, email)));
        paras.add(MailMessages.get(lang, "mail.welcome.body"));
        String base = frontendBase();
        if (base != null) {
            paras.add(MailMessages.get(lang, "mail.signIn") + ": " + base + "/login");
        }
        paras.add(MailMessages.get(lang, "mail.welcome.hint"));
        sendKeyed(email, lang, Style.INVITE, "mail.welcome", paras);
    }

    // N2: báo đã đổi mật khẩu (đặt lại thành công)
    @Async
    public void sendPasswordChangedNotice(String email, String fullName) {
        AppLanguage lang = languageResolver.forEmail(email);
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.greeting", displayName(fullName, email)));
        paras.add(MailMessages.get(lang, "mail.pwChanged.body"));
        paras.add(MailMessages.get(lang, "mail.pwChanged.warn"));
        String base = frontendBase();
        if (base != null) {
            paras.add(MailMessages.get(lang, "mail.resetPassword") + ": " + base + "/forgot-password");
        }
        sendKeyed(email, lang, Style.SECURITY, "mail.pwChanged", paras);
    }

    // N3: tài khoản bị khóa tạm do sai mật khẩu / mã 2FA nhiều lần
    @Async
    public void sendAccountLockedNotice(String email, String fullName, int lockMinutes) {
        AppLanguage lang = languageResolver.forEmail(email);
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.greeting", displayName(fullName, email)));
        paras.add(MailMessages.get(lang, "mail.locked.body", lockMinutes));
        paras.add(MailMessages.get(lang, "mail.locked.warn"));
        String base = frontendBase();
        if (base != null) {
            paras.add(MailMessages.get(lang, "mail.resetPassword") + ": " + base + "/forgot-password");
        }
        sendKeyed(email, lang, Style.SECURITY, "mail.locked", paras);
    }

    // N19: xác nhận check-in Công tắc người chết thành công (nextDueAt có thể null)
    @Async
    public void sendDmsCheckinConfirmation(String email, String fullName, LocalDateTime nextDueAt) {
        AppLanguage lang = languageResolver.forEmail(email);
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.greeting", displayName(fullName, email)));
        paras.add(MailMessages.get(lang, "mail.checkin.body"));
        if (nextDueAt != null) {
            paras.add(MailMessages.get(lang, "mail.checkin.next", formatDeadline(nextDueAt)));
        }
        String base = frontendBase();
        if (base != null) {
            paras.add(MailMessages.get(lang, "mail.openDms") + ": " + base + "/owner/dms");
        }
        sendKeyed(email, lang, Style.SUCCESS, "mail.checkin", paras);
    }

    // N14: báo Owner két sắt đã bị xóa
    @Async
    public void sendVaultDeletedNotice(String email, String fullName, String vaultName) {
        AppLanguage lang = languageResolver.forEmail(email);
        sendKeyed(email, lang, Style.SECURITY, "mail.vaultDeleted", List.of(
                MailMessages.get(lang, "mail.greeting", displayName(fullName, email)),
                MailMessages.get(lang, "mail.vaultDeleted.body", vaultName),
                MailMessages.get(lang, "mail.ifNotYou")));
    }

    // N21: đã lưu / đổi cấu hình Công tắc người chết
    @Async
    public void sendDmsConfiguredNotice(String email, String fullName, int checkinDays, int graceDays, int reminderCount) {
        AppLanguage lang = languageResolver.forEmail(email);
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.greeting", displayName(fullName, email)));
        paras.add(MailMessages.get(lang, "mail.dmsConfigured.body", checkinDays, graceDays, reminderCount));
        paras.add(MailMessages.get(lang, "mail.ifNotYou"));
        String base = frontendBase();
        if (base != null) {
            paras.add(MailMessages.get(lang, "mail.openDms") + ": " + base + "/owner/dms");
        }
        sendKeyed(email, lang, Style.SUCCESS, "mail.dmsConfigured", paras);
    }

    // N22: admin tạm dừng / tiếp tục Công tắc người chết của Owner (reason chỉ có khi tạm dừng, có thể null)
    @Async
    public void sendDmsAdminActionNotice(String email, String fullName, boolean paused, String reason) {
        AppLanguage lang = languageResolver.forEmail(email);
        String prefix = paused ? "mail.dmsPaused" : "mail.dmsResumed";
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.greeting", displayName(fullName, email)));
        paras.add(MailMessages.get(lang, prefix + ".body"));
        if (reason != null && !reason.isBlank()) {
            paras.add(MailMessages.get(lang, "mail.reason", reason));
        }
        String base = frontendBase();
        if (base != null) {
            paras.add(MailMessages.get(lang, "mail.openDms") + ": " + base + "/owner/dms");
        }
        sendKeyed(email, lang, Style.NOTICE, prefix, paras);
    }

    // N26: người thụ hưởng đã đóng phần nhận tài sản
    @Async
    public void sendClaimCompletedNotice(String email, String fullName, String vaultName) {
        AppLanguage lang = languageResolver.forEmail(email);
        sendKeyed(email, lang, Style.SUCCESS, "mail.claimDone", List.of(
                MailMessages.get(lang, "mail.greeting", displayName(fullName, email)),
                MailMessages.get(lang, "mail.claimDone.body", vaultName)));
    }

    // N4: admin khóa / mở khóa tài khoản (reason chỉ có khi khóa, có thể null)
    @Async
    public void sendAccountStatusNotice(String email, String fullName, boolean locked, String reason) {
        AppLanguage lang = languageResolver.forEmail(email);
        String prefix = locked ? "mail.accountLocked" : "mail.accountUnlocked";
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.greeting", displayName(fullName, email)));
        paras.add(MailMessages.get(lang, prefix + ".body"));
        if (locked && reason != null && !reason.isBlank()) {
            paras.add(MailMessages.get(lang, "mail.reason", reason));
        }
        if (locked) {
            paras.add(MailMessages.get(lang, "mail.accountLocked.contact"));
        } else {
            String base = frontendBase();
            if (base != null) {
                paras.add(MailMessages.get(lang, "mail.signIn") + ": " + base + "/login");
            }
        }
        sendKeyed(email, lang, locked ? Style.SECURITY : Style.SUCCESS, prefix, paras);
    }

    // N5: admin cấp / thu hồi vai trò
    @Async
    public void sendRoleChangedNotice(String email, String fullName, boolean granted, String roleCode) {
        AppLanguage lang = languageResolver.forEmail(email);
        String prefix = granted ? "mail.roleGranted" : "mail.roleRevoked";
        sendKeyed(email, lang, Style.SECURITY, prefix, List.of(
                MailMessages.get(lang, "mail.greeting", displayName(fullName, email)),
                MailMessages.get(lang, prefix + ".body", roleCode),
                MailMessages.get(lang, "mail.ifNotYou")));
    }

    // FR-02: mời người thụ hưởng chưa có tài khoản (hoặc chưa xác minh email) đăng ký
    @Async
    public void sendBeneficiaryInvitation(String email, String ownerName, String vaultName) {
        AppLanguage lang = languageResolver.forEmail(email);
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.invite.p1", ownerName, vaultName));
        paras.add(MailMessages.get(lang, "mail.invite.p2"));
        String link = registerLink();
        if (link != null) {
            paras.add(MailMessages.get(lang, "mail.register") + ": " + link);
        }
        sendKeyed(email, lang, Style.INVITE, "mail.invite", paras);
    }

    // FR-02: thông báo cho người thụ hưởng đã có tài khoản
    @Async
    public void sendBeneficiaryAssignedNotice(String email, String ownerName, String vaultName) {
        AppLanguage lang = languageResolver.forEmail(email);
        sendKeyed(email, lang, Style.INVITE, "mail.assigned", List.of(
                MailMessages.get(lang, "mail.assigned.p1", ownerName, vaultName),
                MailMessages.get(lang, "mail.assigned.p2")));
    }

    // N2-owner: báo cho người bị gỡ khỏi vault
    @Async
    public void sendBeneficiaryRemovedNotice(String email, String ownerName, String vaultName) {
        AppLanguage lang = languageResolver.forEmail(email);
        sendKeyed(email, lang, Style.NOTICE, "mail.removed", List.of(
                MailMessages.get(lang, "mail.removed.p1", ownerName, vaultName)));
    }

    /**
     * N2-notify: báo cho người nhận thay khi một phần tài sản được chuyển sang họ
     * (người chính từ chối hoặc không phản hồi kịp). Không nêu danh tính người kia.
     */
    @Async
    public void sendAssetsReassignedNotice(String email, String fullName, String ownerName, String vaultName,
                                           int assetCount, boolean noResponse, UUID vaultId) {
        AppLanguage lang = languageResolver.forEmail(email);
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.greeting", displayName(fullName, email)));
        paras.add(MailMessages.get(lang, "mail.reassigned.p1", assetCount, vaultName, ownerName,
                MailMessages.get(lang, noResponse ? "mail.reassigned.noResponse" : "mail.reassigned.declined")));
        String link = vaultLink(vaultId);
        if (link != null) {
            paras.add(MailMessages.get(lang, "mail.openVault") + ": " + link);
        }
        paras.add(MailMessages.get(lang, "mail.reassigned.p2"));
        sendKeyed(email, lang, Style.NOTICE, "mail.reassigned", paras);
    }

    // FR-02 (bước 7): xác nhận cho Owner
    @Async
    public void sendBeneficiaryAssignmentConfirmation(String ownerEmail, String vaultName,
                                                      String beneficiaryEmail, boolean pendingRegistration) {
        AppLanguage lang = languageResolver.forEmail(ownerEmail);
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.assignConfirm.p1", beneficiaryEmail, vaultName));
        if (pendingRegistration) {
            paras.add(MailMessages.get(lang, "mail.assignConfirm.pending"));
        }
        sendKeyed(ownerEmail, lang, Style.SUCCESS, "mail.assignConfirm", paras);
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
        AppLanguage lang = languageResolver.forEmail(email);
        String deadline = formatDeadline(claimDeadlineAt);
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.unlocked.greeting", fullName == null ? "" : fullName));
        paras.add(MailMessages.get(lang, "mail.unlocked.intro", ownerName, vaultName));
        if (!accountActive) {
            paras.add(MailMessages.get(lang, "mail.unlocked.inactive"));
            String registerLink = registerLink();
            if (registerLink != null) {
                paras.add(MailMessages.get(lang, "mail.register") + ": " + registerLink);
            }
        }
        String link = vaultLink(vaultId);
        if (link != null) {
            paras.add(MailMessages.get(lang, "mail.openVault") + ": " + link);
        }
        paras.add(MailMessages.get(lang, "mail.unlocked.stepsTitle") + "\n" + MailMessages.get(lang, "mail.unlocked.steps"));
        paras.add(MailMessages.get(lang, "mail.unlocked.deadline", deadline) + "\n"
                + MailMessages.get(lang, "mail.unlocked.deadlineNote"));
        deliver(email, lang, Style.LEGACY, MailMessages.get(lang, "mail.unlocked.subject"),
                MailMessages.get(lang, "mail.unlocked.title"), paras, null,
                MailMessages.get(lang, "mail.unlocked.noShare"));
    }

    // N2-exec: Executor nhắc người thụ hưởng chưa phản hồi việc nhận tài sản
    @Async
    public void sendBeneficiaryClaimReminder(String email, String fullName, String ownerName, String vaultName,
                                             LocalDateTime claimDeadlineAt, UUID vaultId) {
        AppLanguage lang = languageResolver.forEmail(email);
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.greeting", displayName(fullName, email)));
        paras.add(MailMessages.get(lang, "mail.claimReminder.p1", ownerName, vaultName));
        String link = vaultLink(vaultId);
        if (link != null) {
            paras.add(MailMessages.get(lang, "mail.openVault") + ": " + link);
        }
        paras.add(MailMessages.get(lang, "mail.claimReminder.p2", formatDeadline(claimDeadlineAt)));
        paras.add(MailMessages.get(lang, "mail.noShare"));
        sendKeyed(email, lang, Style.URGENT, "mail.claimReminder", paras);
    }

    // F4b: thông báo khi lời nhắn di nguyện / Time-Capsule đến hạn mở cho người thụ hưởng
    // (n1: không để lộ tiêu đề riêng tư trong mail; m4: bổ sung link đăng ký nếu người nhận chưa có tài khoản)
    @Async
    public void sendLegacyMessageDeliveredNotification(String email, String fullName, String ownerName,
                                                       String vaultName, UUID vaultId, boolean hasAccount) {
        AppLanguage lang = languageResolver.forEmail(email);
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.greeting", displayName(fullName, email)));
        paras.add(MailMessages.get(lang, "mail.legacyMsg.p1", ownerName, vaultName));
        if (!hasAccount) {
            paras.add(MailMessages.get(lang, "mail.legacyMsg.inactive"));
            String regLink = registerLink();
            if (regLink != null) {
                paras.add(MailMessages.get(lang, "mail.register") + ": " + regLink);
            }
        }
        String link = vaultLink(vaultId);
        if (link != null) {
            paras.add(MailMessages.get(lang, "mail.openVault") + ": " + link);
        }
        paras.add(MailMessages.get(lang, "mail.legacyMsg.p2"));
        paras.add(MailMessages.get(lang, "mail.noShare"));
        sendKeyed(email, lang, Style.LEGACY, "mail.legacyMsg", paras);
    }

    public void sendVerificationRequestNotification(String email, UUID requestId) {
        AppLanguage lang = languageResolver.forEmail(email);
        sendKeyed(email, lang, Style.CASE, "mail.verifyRequest",
                List.of(MailMessages.get(lang, "mail.verifyRequest.body", requestId)));
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
        AppLanguage lang = languageResolver.forEmail(email);
        String base = frontendBase();
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.dmsReminder.p1", formatDeadline(checkinDueAt), formatDeadline(triggerAt)));
        if (base != null && checkinToken != null && !checkinToken.isBlank()) {
            paras.add(MailMessages.get(lang, "mail.dmsReminder.link"));
            paras.add(MailMessages.get(lang, "mail.dmsReminder.button") + ": "
                    + base + "/dms/checkin?token=" + URLEncoder.encode(checkinToken, StandardCharsets.UTF_8));
        }
        paras.add(MailMessages.get(lang, "mail.dmsReminder.p2", reminderNumber, reminderCount));
        deliver(email, lang, Style.URGENT,
                MailMessages.get(lang, "mail.dmsReminder.subject", reminderNumber, reminderCount),
                MailMessages.get(lang, "mail.dmsReminder.title", reminderNumber, reminderCount),
                paras, null, null);
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
        AppLanguage lang = languageResolver.forEmail(confirmerEmail);
        String confirmUrl = base + "/dms/confirm?token=" + URLEncoder.encode(confirmationToken, StandardCharsets.UTF_8);
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.greeting", confirmerName == null ? "" : confirmerName));
        paras.add(MailMessages.get(lang, "mail.crossConfirm.p1", ownerLabel(ownerName, ownerEmail)));
        paras.add(MailMessages.get(lang, "mail.crossConfirm.p2", formatDeadline(deadlineAt)));
        paras.add(MailMessages.get(lang, "mail.crossConfirm.button") + ": " + confirmUrl);
        paras.add(MailMessages.get(lang, "mail.crossConfirm.p3"));
        deliver(confirmerEmail, lang, Style.URGENT,
                MailMessages.get(lang, "mail.crossConfirm.subject", ownerDisplayName(ownerName, ownerEmail)),
                MailMessages.get(lang, "mail.crossConfirm.title"), paras, null, null);
    }

    /**
     * X2 (m5): báo Owner rằng người xác nhận chéo đang được liên hệ; Owner check-in trước hạn là huỷ quy trình.
     */
    public void sendOwnerConfirmationPendingNotice(String ownerEmail, String ownerName, List<String> confirmerNames,
                                                   LocalDateTime deadlineAt) {
        AppLanguage lang = languageResolver.forEmail(ownerEmail);
        String base = frontendBase();
        String names = confirmerNames == null || confirmerNames.isEmpty()
                ? MailMessages.get(lang, "mail.pendingConfirm.names") : String.join(", ", confirmerNames);
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.greeting", ownerDisplayName(ownerName, ownerEmail)));
        paras.add(MailMessages.get(lang, "mail.pendingConfirm.body", names, formatDeadline(deadlineAt)));
        if (base != null) {
            paras.add(MailMessages.get(lang, "mail.pendingConfirm.button") + ": " + base + "/owner/dms");
        }
        sendKeyed(ownerEmail, lang, Style.URGENT, "mail.pendingConfirm", paras);
    }

    /**
     * X2: thông báo cho Owner biết người xác nhận chéo vừa báo "Owner vẫn bình an" và hoãn DMS 7 ngày.
     */
    public void sendOwnerConfirmedSafeNotice(String ownerEmail, String confirmerName, LocalDateTime postponedUntil) {
        AppLanguage lang = languageResolver.forEmail(ownerEmail);
        String name = confirmerName == null ? MailMessages.get(lang, "mail.confirmedSafe.name") : confirmerName;
        sendKeyed(ownerEmail, lang, Style.SUCCESS, "mail.confirmedSafe", List.of(
                MailMessages.get(lang, "mail.confirmedSafe.body", name, formatDeadline(postponedUntil))));
    }

    public void sendVerificationMessageNotification(String email, UUID requestId) {
        AppLanguage lang = languageResolver.forEmail(email);
        sendKeyed(email, lang, Style.CASE, "mail.verifyMessage",
                List.of(MailMessages.get(lang, "mail.verifyMessage.body", requestId)));
    }

    // FR-06: method đồng bộ; bản thân listener AFTER_COMMIT đã chạy @Async, lỗi do listener bắt và log.
    // Không nêu tên Executor còn lại của Owner.
    public void sendExecutorAssignedNotice(String to, String ownerName, String ownerEmail, ExecutorType type) {
        AppLanguage lang = languageResolver.forEmail(to);
        sendKeyed(to, lang, Style.CASE, "mail.execAssigned", List.of(MailMessages.get(lang, "mail.execAssigned.body",
                ownerLabel(ownerName, ownerEmail), roleLabel(lang, type), ownerDisplayName(ownerName, ownerEmail))));
    }

    public void sendExecutorRevokedNotice(String to, String ownerName, String ownerEmail, ExecutorType type) {
        AppLanguage lang = languageResolver.forEmail(to);
        sendKeyed(to, lang, Style.NOTICE, "mail.execRevoked", List.of(MailMessages.get(lang, "mail.execRevoked.body",
                ownerLabel(ownerName, ownerEmail), roleLabel(lang, type), ownerDisplayName(ownerName, ownerEmail))));
    }

    // FR-07 / BR-03: hệ thống tự thu hồi vì Primary không xác nhận trong hạn, Owner không làm gì (thường đã mất)
    public void sendExecutorTimeoutRevokedNotice(String to, String ownerName, String ownerEmail, int waitDays) {
        AppLanguage lang = languageResolver.forEmail(to);
        sendKeyed(to, lang, Style.NOTICE, "mail.execTimeout", List.of(MailMessages.get(lang, "mail.execTimeout.body",
                ownerLabel(ownerName, ownerEmail), waitDays, ownerDisplayName(ownerName, ownerEmail))));
    }

    // FR-07: Admin chỉ định Executor mới để tiếp tục bàn giao (người cũ bỏ dở hoặc không còn dùng được)
    public void sendExecutorReplacedByAdminNotice(String to, String ownerName, String ownerEmail,
                                                  ExecutorType type) {
        AppLanguage lang = languageResolver.forEmail(to);
        sendKeyed(to, lang, Style.NOTICE, "mail.execReplaced", List.of(MailMessages.get(lang, "mail.execReplaced.body",
                roleLabel(lang, type), ownerLabel(ownerName, ownerEmail), ownerDisplayName(ownerName, ownerEmail))));
    }

    // N9: thanh toán thành công, đính kèm hóa đơn PDF (pdf null thì gửi không đính kèm)
    @Async
    public void sendPaymentSucceededNotice(String to, String fullName, String planName, long amountVnd,
                                           String receiptNo, byte[] pdf) {
        AppLanguage lang = languageResolver.forEmail(to);
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.greeting", displayName(fullName, to)));
        paras.add(MailMessages.get(lang, "mail.paymentOk.body", planName, String.format(java.util.Locale.US, "%,d", amountVnd), receiptNo));
        if (pdf != null) {
            paras.add(MailMessages.get(lang, "mail.paymentOk.attached"));
        }
        deliver(to, lang, Style.BILLING, MailMessages.get(lang, "mail.paymentOk.subject"),
                MailMessages.get(lang, "mail.paymentOk.title"), paras, null, null,
                "hoa-don-" + receiptNo + ".pdf", pdf);
    }

    // N10: thanh toán thất bại hoặc bị hủy
    @Async
    public void sendPaymentFailedNotice(String to, String fullName) {
        AppLanguage lang = languageResolver.forEmail(to);
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.greeting", displayName(fullName, to)));
        paras.add(MailMessages.get(lang, "mail.paymentFailed.body"));
        String base = frontendBase();
        if (base != null) {
            paras.add(MailMessages.get(lang, "mail.paymentFailed.button") + ": " + base + "/pricing");
        }
        sendKeyed(to, lang, Style.NOTICE, "mail.paymentFailed", paras);
    }

    // N11: gói VIP đã kích hoạt (expiresAt null thì không ghi ngày)
    @Async
    public void sendVipActivatedNotice(String to, String fullName, String planName, LocalDateTime expiresAt) {
        AppLanguage lang = languageResolver.forEmail(to);
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.greeting", displayName(fullName, to)));
        paras.add(MailMessages.get(lang, "mail.vipActive.body", planName));
        if (expiresAt != null) {
            paras.add(MailMessages.get(lang, "mail.vipActive.until", formatDeadline(expiresAt)));
        }
        sendKeyed(to, lang, Style.SUCCESS, "mail.vipActive", paras);
    }

    /** N12: gói đã hết hạn. Không @Async, người gọi tự bắt lỗi từng mail như mail nhắc trước hạn. */
    public void sendSubscriptionExpiredNotice(String to, String fullName, String planName) {
        AppLanguage lang = languageResolver.forEmail(to);
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.greeting", displayName(fullName, to)));
        paras.add(MailMessages.get(lang, "mail.subExpired.body", planName));
        String base = frontendBase();
        if (base != null) {
            paras.add(MailMessages.get(lang, "mail.subExpiry.button") + ": " + base + "/pricing");
        }
        sendKeyed(to, lang, Style.URGENT, "mail.subExpired", paras);
    }

    // T1: Nhắc Owner trước 7 ngày khi gói VIP sắp hết hạn
    public void sendSubscriptionExpiryReminder(String to, String ownerName, String planName, LocalDateTime expiresAt) {
        AppLanguage lang = languageResolver.forEmail(to);
        String base = frontendBase();
        List<String> paras = new ArrayList<>();
        paras.add(MailMessages.get(lang, "mail.greeting", ownerDisplayName(ownerName, to)));
        paras.add(MailMessages.get(lang, "mail.subExpiry.body", planName, formatDeadline(expiresAt)));
        if (base != null) {
            paras.add(MailMessages.get(lang, "mail.subExpiry.button") + ": " + base + "/billing");
        }
        sendKeyed(to, lang, Style.URGENT, "mail.subExpiry", paras);
    }

    private static String roleLabel(AppLanguage lang, ExecutorType type) {
        return MailMessages.get(lang, type == ExecutorType.PRIMARY ? "mail.role.PRIMARY" : "mail.role.BACKUP");
    }

    // FR-06: Owner tạo trước khi có fullName (null/rỗng) thì hiển thị email, không in chữ "null".
    private static String ownerDisplayName(String ownerName, String ownerEmail) {
        return ownerName == null || ownerName.isBlank() ? ownerEmail : ownerName;
    }

    // "Tên (email)"; không có tên thì chỉ "email".
    private static String ownerLabel(String ownerName, String ownerEmail) {
        return ownerName == null || ownerName.isBlank() ? ownerEmail : ownerName + " (" + ownerEmail + ")";
    }

    /** Mail theo khóa prefix.subject / prefix.title; các đoạn thân thư do người gọi dựng sẵn (đã dịch). */
    private void sendKeyed(String to, AppLanguage lang, Style style, String prefix, List<String> paragraphs) {
        deliver(to, lang, style, MailMessages.get(lang, prefix + ".subject"),
                MailMessages.get(lang, prefix + ".title"), paragraphs, null, null);
    }

    private static String displayName(String fullName, String email) {
        return fullName == null || fullName.isBlank() ? email : fullName;
    }

    /** Mail có mã OTP: chữ lấy từ mail_xx.properties theo khóa prefix.subject/.title/.body. */
    private void sendCode(String to, AppLanguage lang, Style style, String prefix, String code,
                          List<String> extraParagraphs, String footerNote) {
        List<String> paras = new ArrayList<>();
        if (MailMessages.has(lang, prefix + ".body")) {
            paras.add(MailMessages.get(lang, prefix + ".body"));
        }
        paras.addAll(extraParagraphs);
        deliver(to, lang, style, MailMessages.get(lang, prefix + ".subject"),
                MailMessages.get(lang, prefix + ".title"), paras, code, footerNote);
    }

    private void deliver(String to, AppLanguage lang, Style style, String subject, String title,
                         List<String> paragraphs, String code, String footerNote) {
        deliver(to, lang, style, subject, title, paragraphs, code, footerNote, null, null);
    }

    private void deliver(String to, AppLanguage lang, Style style, String subject, String title,
                         List<String> paragraphs, String code, String footerNote,
                         String pdfName, byte[] pdf) {
        String brand = MailMessages.get(lang, "mail.brand");
        String html = MailHtml.render(style, title, paragraphs, code, footerNote, brand);
        String text = MailHtml.renderText(title, paragraphs, code, footerNote);
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            if (mailFrom != null && !mailFrom.isBlank()) {
                helper.setFrom(mailFrom);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            if (pdf != null) {
                helper.addAttachment(pdfName, new org.springframework.core.io.ByteArrayResource(pdf), "application/pdf");
            }
            javaMailSender.send(message);
        } catch (MessagingException e) {
            throw new MailPreparationException(e);
        }
    }
}
