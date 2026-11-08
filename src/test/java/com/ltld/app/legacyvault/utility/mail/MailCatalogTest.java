package com.ltld.app.legacyvault.utility.mail;

import com.ltld.app.legacyvault.enums.ExecutorType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Khung email HTML: đủ 21 mail ở cả hai ngôn ngữ, không sót {0}, escape dữ liệu người dùng. */
class MailCatalogTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDateTime AT = LocalDateTime.of(2026, 12, 31, 10, 30);
    private static final MailLayout.Brand BRAND = new MailLayout.Brand("https://legacyvault.codes/brand/logo-mark.png", "https://legacyvault.codes");

    private static List<MailContent> all(MailCatalog c) {
        return List.of(
                c.otp("123456"), c.passwordReset("654321"),
                c.beneficiaryInvitation("An", "Vault"), c.beneficiaryAssigned("An", "Vault"), c.beneficiaryRemoved("An", "Vault"),
                c.assetsReassigned("Bình", "An", "Vault", 2, false, ID), c.beneficiaryAssignmentConfirmation("Vault", "b@x.vn", false),
                c.vaultUnlocked(null, "An", "Vault", AT, false, ID), c.beneficiaryClaimReminder("Bình", "An", "Vault", AT, ID),
                c.legacyMessageDelivered("", "An", "Vault", ID, false), c.verificationRequest(ID), c.verificationMessage(ID),
                c.dmsCheckinReminder(AT, AT.plusDays(7), 3, 3, "tok"), c.dmsCrossConfirmationRequest(null, "An", "a@x.vn", AT, "tok"),
                c.ownerConfirmationPending(null, "a@x.vn", List.of(), AT), c.ownerConfirmedSafe(null, AT),
                c.executorAssigned("An", "a@x.vn", ExecutorType.PRIMARY), c.executorRevoked("An", "a@x.vn", ExecutorType.BACKUP),
                c.executorTimeoutRevoked("An", "a@x.vn", 14), c.executorReplacedByAdmin("An", "a@x.vn", ExecutorType.BACKUP),
                c.subscriptionExpiry("a@x.vn", null, "VIP", AT));
    }

    @Test
    void everyMail_rendersInBothLanguages_withoutPlaceholdersOrNull() {
        for (Locale locale : List.of(MailText.VI, MailText.EN)) {
            MailCatalog c = new MailCatalog(new MailText(locale), "https://legacyvault.codes/");
            List<MailContent> mails = all(c);
            assertThat(mails).hasSize(21);
            for (MailContent m : mails) {
                MailLayout.Rendered r = MailLayout.render(m, c.text(), BRAND);
                assertThat(r.subject()).isNotBlank().doesNotContain("{").endsWith("LegacyVault");
                assertThat(r.plain()).doesNotContain("{0}", "{1}", "null");
                assertThat(r.html()).startsWith("<!doctype html>").doesNotContain("{0}").contains("legacyvault.codes/brand/logo-mark.png");
            }
        }
    }

    @Test
    void vietnameseIsDefault_andEnglishIsAvailable() {
        assertThat(new MailCatalog(new MailText(null), null).otp("1").subject()).isEqualTo("Mã xác thực OTP - LegacyVault");
        assertThat(new MailCatalog(new MailText(MailText.EN), null).otp("1").subject()).isEqualTo("OTP verification code - LegacyVault");
    }

    @Test
    void userDataIsEscapedInHtml() {
        MailCatalog c = new MailCatalog(new MailText(MailText.EN), null);
        String html = MailLayout.render(c.beneficiaryInvitation("<script>alert(1)</script>", "A&B"), c.text(), BRAND).html();
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;").contains("A&amp;B");
    }

    @Test
    void otpMail_showsEachDigitInItsOwnBox() {
        MailCatalog c = new MailCatalog(new MailText(MailText.VI), null);
        String html = MailLayout.render(c.otp("482915"), c.text(), BRAND).html();
        assertThat(html.split("class=\"lv-otp\"", -1)).hasSize(7);
    }

    @Test
    void crossConfirmation_withoutFrontend_throws() {
        MailCatalog c = new MailCatalog(new MailText(MailText.VI), "");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> c.dmsCrossConfirmationRequest("L", "An", "a@x.vn", AT, "t")).isInstanceOf(IllegalStateException.class);
    }
}
