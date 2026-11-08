package com.ltld.app.legacyvault.utility.mail;

import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.utility.mail.MailContent.Tone;
import com.ltld.app.legacyvault.utility.mail.MailText.Txt;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Nội dung của 21 loại email hiện có (mỗi hàm public của EmailSender có một hàm tương ứng ở đây).
 * Không phụ thuộc Spring nên dựng/xem trước được bằng unit test thuần. Chữ lấy từ mail*.properties theo ngôn ngữ.
 *
 * Nhóm giao diện (Phần D của kế hoạch) thể hiện qua kicker + ô hành động:
 *   mã xác thực (ô mã), thiệp mời (nút), cảnh báo/khẩn (kicker đỏ/vàng), xác nhận (kicker xanh lá),
 *   bàn giao/thư di nguyện (kicker vàng kim), công việc/hồ sơ (bảng thông tin + nút).
 */
public final class MailCatalog {

    public static final int OTP_EXPIRE_MINUTES = 5;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final MailText t;
    private final String base;   // địa chỉ frontend không có "/" cuối; null nếu chưa cấu hình

    public MailCatalog(MailText t, String frontendBaseUrl) {
        this.t = t;
        this.base = normalizeBase(frontendBaseUrl);
    }

    public MailText text() {
        return t;
    }

    /** Hạn theo giờ Việt Nam: các mốc LocalDateTime theo múi giờ máy chủ nên đổi sang GMT+7. */
    public static String formatDeadline(LocalDateTime at) {
        return at.atZone(ZoneId.systemDefault()).withZoneSameInstant(VN_ZONE).format(DEADLINE_FORMAT);
    }

    /* ======================= Mã xác thực ======================= */

    public MailContent otp(String otp) {
        return MailContent.create(t.plain("otp.subject"))
                .preheader(t.plain("otp.preheader", otp))
                .kicker(Tone.INFO, t.plain("kicker.security"))
                .greeting(t.txt("common.helloAnon"))
                .headline(t.txt("otp.headline"))
                .code(t.plain("otp.codeLabel"), otp, t.txt("otp.validFor", OTP_EXPIRE_MINUTES))
                .note(t.txt("otp.noShare"));
    }

    public MailContent passwordReset(String otp) {
        return MailContent.create(t.plain("reset.subject"))
                .preheader(t.plain("reset.preheader", otp))
                .kicker(Tone.WARN, t.plain("kicker.security"))
                .greeting(t.txt("common.helloAnon"))
                .headline(t.txt("reset.headline"))
                .code(t.plain("otp.codeLabel"), otp, t.txt("otp.validFor", OTP_EXPIRE_MINUTES))
                .para(t.txt("reset.notYou"))
                .note(t.txt("otp.noShare"));
    }

    /* ======================= Người thụ hưởng ======================= */

    public MailContent beneficiaryInvitation(String ownerName, String vaultName) {
        return MailContent.create(t.plain("benInvite.subject"))
                .preheader(t.plain("benInvite.preheader", ownerName))
                .kicker(Tone.INFO, t.plain("kicker.invitation"))
                .greeting(t.txt("common.helloAnon"))
                .headline(t.txt("ben.named", ownerName, vaultName))
                .button(t.plain("benInvite.button"), registerLink(), t.txt("common.vaultCaption", vaultName))
                .para(t.txt("benInvite.body"))
                .para(t.txt("benInvite.ignore"));
    }

    public MailContent beneficiaryAssigned(String ownerName, String vaultName) {
        return MailContent.create(t.plain("benAssigned.subject"))
                .kicker(Tone.INFO, t.plain("kicker.invitation"))
                .greeting(t.txt("common.helloAnon"))
                .headline(t.txt("ben.named", ownerName, vaultName))
                .para(t.txt("benAssigned.body"));
    }

    public MailContent beneficiaryRemoved(String ownerName, String vaultName) {
        return MailContent.create(t.plain("benRemoved.subject"))
                .kicker(Tone.INFO, t.plain("kicker.statusChange"))
                .greeting(t.txt("common.helloAnon"))
                .headline(t.txt("benRemoved.headline", ownerName, vaultName));
    }

    public MailContent assetsReassigned(String fullName, String ownerName, String vaultName,
                                        int assetCount, boolean noResponse, UUID vaultId) {
        return MailContent.create(t.plain("reassigned.subject"))
                .kicker(Tone.FORMAL, t.plain("kicker.handover"))
                .greeting(hello(fullName))
                .headline(t.txt("reassigned.headline", assetCount, vaultName, ownerName))
                .button(t.plain("common.openVault"), vaultLink(vaultId), null)
                .para(t.txt(noResponse ? "reassigned.reasonNoResponse" : "reassigned.reasonDeclined"))
                .para(t.txt("reassigned.next"));
    }

    public MailContent beneficiaryAssignmentConfirmation(String vaultName, String beneficiaryEmail,
                                                         boolean pendingRegistration) {
        MailContent c = MailContent.create(t.plain("benConfirm.subject"))
                .kicker(Tone.SUCCESS, t.plain("kicker.confirmed"))
                .greeting(t.txt("common.helloAnon"))
                .headline(t.txt("benConfirm.headline", beneficiaryEmail, vaultName));
        if (pendingRegistration) c.para(t.txt("benConfirm.pending"));
        return c;
    }

    public MailContent vaultUnlocked(String fullName, String ownerName, String vaultName,
                                     LocalDateTime claimDeadlineAt, boolean accountActive, UUID vaultId) {
        MailContent c = MailContent.create(t.plain("unlocked.subject"))
                .preheader(t.plain("unlocked.preheader", ownerName))
                .kicker(Tone.FORMAL, t.plain("kicker.handover"))
                .greeting(hello(fullName))
                .headline(t.txt("unlocked.headline", vaultName, ownerName))
                .button(t.plain("common.openVault"), vaultLink(vaultId),
                        t.txt("unlocked.deadlineCaption", formatDeadline(claimDeadlineAt)));
        if (!accountActive) {
            c.para(t.txt("unlocked.notActivated")).link(t.plain("common.registerAt"), registerLink());
        }
        return c.steps(t.plain("unlocked.stepsTitle"), List.of(
                        t.txt("unlocked.step1"), t.txt("unlocked.step2"), t.txt("unlocked.step3")))
                .rows(rows(t.plain("common.claimDeadline"), formatDeadline(claimDeadlineAt) + " (GMT+7)"))
                .para(t.txt("unlocked.archiveWarning"))
                .note(t.txt("common.noShareEmail"));
    }

    public MailContent beneficiaryClaimReminder(String fullName, String ownerName, String vaultName,
                                                LocalDateTime claimDeadlineAt, UUID vaultId) {
        return MailContent.create(t.plain("claimReminder.subject"))
                .kicker(Tone.WARN, t.plain("kicker.reminder"))
                .greeting(hello(fullName))
                .headline(t.txt("claimReminder.headline", ownerName, vaultName))
                .button(t.plain("common.openVault"), vaultLink(vaultId), null)
                .para(t.txt("claimReminder.next"))
                .rows(rows(t.plain("common.claimDeadline"), formatDeadline(claimDeadlineAt) + " (GMT+7)"))
                .note(t.txt("common.noShareEmail"));
    }

    public MailContent legacyMessageDelivered(String fullName, String ownerName, String vaultName,
                                              UUID vaultId, boolean hasAccount) {
        MailContent c = MailContent.create(t.plain("legacyMsg.subject"))
                .preheader(t.plain("legacyMsg.preheader", ownerName))
                .kicker(Tone.FORMAL, t.plain("kicker.legacyMessage"))
                .greeting(hello(fullName))
                .headline(t.txt("legacyMsg.headline", ownerName, vaultName))
                .button(t.plain("common.openVault"), vaultLink(vaultId), null);
        if (!hasAccount) {
            c.para(t.txt("legacyMsg.notActivated")).link(t.plain("common.registerAt"), registerLink());
        }
        return c.para(t.txt("legacyMsg.next")).note(t.txt("common.noShareEmail"));
    }

    /* ======================= Xác minh pháp lý ======================= */

    public MailContent verificationRequest(UUID requestId) {
        return MailContent.create(t.plain("verifyRequest.subject"))
                .kicker(Tone.INFO, t.plain("kicker.case"))
                .greeting(t.txt("common.helloAnon"))
                .headline(t.txt("verifyRequest.headline"))
                .button(t.plain("verifyRequest.button"), link("/verifier/request/" + requestId), null)
                .rows(rows(t.plain("common.requestId"), String.valueOf(requestId)))
                .para(t.txt("common.signInForDetails"));
    }

    public MailContent verificationMessage(UUID requestId) {
        return MailContent.create(t.plain("verifyMessage.subject"))
                .kicker(Tone.INFO, t.plain("kicker.case"))
                .greeting(t.txt("common.helloAnon"))
                .headline(t.txt("verifyMessage.headline"))
                .button(t.plain("common.signIn"), link("/login"), null)
                .rows(rows(t.plain("common.requestId"), String.valueOf(requestId)))
                .para(t.txt("common.signInForDetails"));
    }

    /* ======================= Dead Man's Switch ======================= */

    public MailContent dmsCheckinReminder(LocalDateTime checkinDueAt, LocalDateTime triggerAt,
                                          int reminderNumber, int reminderCount, String checkinToken) {
        String oneClick = (base != null && checkinToken != null && !checkinToken.isBlank())
                ? base + "/dms/checkin?token=" + URLEncoder.encode(checkinToken, StandardCharsets.UTF_8) : null;
        return MailContent.create(t.plain("dmsReminder.subject", reminderNumber, reminderCount))
                .preheader(t.plain("dmsReminder.preheader", formatDeadline(checkinDueAt)))
                .kicker(reminderNumber >= reminderCount ? Tone.DANGER : Tone.WARN,
                        t.plain("dmsReminder.kicker", reminderNumber, reminderCount))
                .greeting(t.txt("common.helloAnon"))
                .headline(t.txt("dmsReminder.headline"))
                .button(t.plain("dmsReminder.button"), oneClick, t.txt("dmsReminder.buttonCaption"))
                .rows(rows(t.plain("dmsReminder.deadlineLabel"), formatDeadline(checkinDueAt) + " " + t.plain("common.vnTime")))
                .para(t.txt("dmsReminder.trigger", formatDeadline(triggerAt)))
                .para(t.txt("dmsReminder.howTo"))
                .para(t.txt("dmsReminder.count", reminderNumber, reminderCount));
    }

    public MailContent dmsCrossConfirmationRequest(String confirmerName, String ownerName, String ownerEmail,
                                                   LocalDateTime deadlineAt, String confirmationToken) {
        if (base == null) {
            throw new IllegalStateException("app.frontend.base-url is not configured; cannot build the confirmation link");
        }
        String confirmUrl = base + "/dms/confirm?token=" + URLEncoder.encode(confirmationToken, StandardCharsets.UTF_8);
        return MailContent.create(t.plain("crossConfirm.subject", ownerDisplayName(ownerName, ownerEmail)))
                .preheader(t.plain("crossConfirm.preheader", ownerDisplayName(ownerName, ownerEmail)))
                .kicker(Tone.DANGER, t.plain("kicker.urgent"))
                .greeting(hello(confirmerName))
                .headline(t.txt("crossConfirm.headline", ownerLabel(ownerName, ownerEmail)))
                .para(t.txt("crossConfirm.expired"))
                .para(t.txt("crossConfirm.ask", formatDeadline(deadlineAt)))
                .para(t.txt("crossConfirm.safeLink"))
                .note(t.txt("crossConfirm.timeout"))
                .button(t.plain("crossConfirm.button"), confirmUrl, null);
    }

    public MailContent ownerConfirmationPending(String ownerName, String ownerEmail, List<String> confirmerNames,
                                                LocalDateTime deadlineAt) {
        String names = confirmerNames == null || confirmerNames.isEmpty()
                ? t.plain("ownerPending.defaultNames") : String.join(", ", confirmerNames);
        return MailContent.create(t.plain("ownerPending.subject"))
                .kicker(Tone.WARN, t.plain("kicker.dms"))
                .greeting(t.txt("common.hello", ownerDisplayName(ownerName, ownerEmail)))
                .headline(t.txt("ownerPending.headline"))
                .button(t.plain("ownerPending.button"), link("/owner/dms"), null)
                .para(t.txt("ownerPending.contacting", names))
                .para(t.txt("ownerPending.cancel", formatDeadline(deadlineAt)));
    }

    public MailContent ownerConfirmedSafe(String confirmerName, LocalDateTime postponedUntil) {
        String who = confirmerName == null ? t.plain("ownerSafe.defaultName") : confirmerName;
        return MailContent.create(t.plain("ownerSafe.subject"))
                .kicker(Tone.SUCCESS, t.plain("kicker.dms"))
                .greeting(t.txt("common.helloAnon"))
                .headline(t.txt("ownerSafe.headline", who))
                .button(t.plain("ownerPending.button"), link("/owner/dms"), null)
                .para(t.txt("ownerSafe.postponed", formatDeadline(postponedUntil)))
                .para(t.txt("ownerSafe.next"));
    }

    /* ======================= Executor ======================= */

    public MailContent executorAssigned(String ownerName, String ownerEmail, ExecutorType type) {
        return MailContent.create(t.plain("execAssigned.subject"))
                .kicker(Tone.INFO, t.plain("kicker.executor"))
                .greeting(t.txt("common.helloAnon"))
                .headline(t.txt("execAssigned.headline", ownerLabel(ownerName, ownerEmail), role(type)))
                .para(t.txt("execAssigned.nothingNow", ownerDisplayName(ownerName, ownerEmail)))
                .para(t.txt("execAssigned.unknown"));
    }

    public MailContent executorRevoked(String ownerName, String ownerEmail, ExecutorType type) {
        return MailContent.create(t.plain("execRevoked.subject"))
                .kicker(Tone.INFO, t.plain("kicker.statusChange"))
                .greeting(t.txt("common.helloAnon"))
                .headline(t.txt("execRevoked.headline", ownerLabel(ownerName, ownerEmail), role(type)))
                .para(t.txt("exec.noLonger", ownerDisplayName(ownerName, ownerEmail)));
    }

    public MailContent executorTimeoutRevoked(String ownerName, String ownerEmail, int waitDays) {
        return MailContent.create(t.plain("execEnded.subject"))
                .kicker(Tone.INFO, t.plain("kicker.statusChange"))
                .greeting(t.txt("common.helloAnon"))
                .headline(t.txt("execTimeout.headline", ownerLabel(ownerName, ownerEmail), waitDays))
                .para(t.txt("exec.noLonger", ownerDisplayName(ownerName, ownerEmail)));
    }

    public MailContent executorReplacedByAdmin(String ownerName, String ownerEmail, ExecutorType type) {
        return MailContent.create(t.plain("execEnded.subject"))
                .kicker(Tone.INFO, t.plain("kicker.statusChange"))
                .greeting(t.txt("common.helloAnon"))
                .headline(t.txt("execReplaced.headline", role(type), ownerLabel(ownerName, ownerEmail)))
                .para(t.txt("exec.noLonger", ownerDisplayName(ownerName, ownerEmail)));
    }

    /* ======================= Gói thuê bao ======================= */

    public MailContent subscriptionExpiry(String to, String ownerName, String planName, LocalDateTime expiresAt) {
        return MailContent.create(t.plain("subExpiry.subject"))
                .preheader(t.plain("subExpiry.preheader", planName, formatDeadline(expiresAt)))
                .kicker(Tone.WARN, t.plain("kicker.reminder"))
                .greeting(t.txt("common.hello", ownerDisplayName(ownerName, to)))
                .headline(t.txt("subExpiry.headline", planName, formatDeadline(expiresAt)))
                .button(t.plain("subExpiry.button"), link("/billing"), null)
                .para(t.txt("subExpiry.keepWorking"))
                .para(t.txt("subExpiry.renew"));
    }

    /* ======================= Tiện ích ======================= */

    private Txt hello(String name) {
        return name == null || name.isBlank() ? t.txt("common.helloAnon") : t.txt("common.hello", name);
    }

    private String role(ExecutorType type) {
        return t.plain(type == ExecutorType.PRIMARY ? "exec.rolePrimary" : "exec.roleBackup");
    }

    private String link(String path) {
        return base == null ? null : base + path;
    }

    private String vaultLink(UUID vaultId) {
        return vaultId == null ? null : link("/beneficiary/vaults/" + vaultId);
    }

    private String registerLink() {
        return link("/register");
    }

    private static List<String[]> rows(String label, String value) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{label, value});
        return rows;
    }

    static String normalizeBase(String url) {
        if (url == null || url.isBlank()) return null;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // Owner tạo trước khi có fullName (null/rỗng) thì hiển thị email, không in chữ "null".
    static String ownerDisplayName(String ownerName, String ownerEmail) {
        return ownerName == null || ownerName.isBlank() ? ownerEmail : ownerName;
    }

    // "Tên (email)"; không có tên thì chỉ "email".
    static String ownerLabel(String ownerName, String ownerEmail) {
        return ownerName == null || ownerName.isBlank() ? ownerEmail : ownerName + " (" + ownerEmail + ")";
    }
}
