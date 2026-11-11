package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigRequest;
import com.ltld.app.legacyvault.dto.identitydto.PhoneOtpResponse;
import com.ltld.app.legacyvault.dto.identitydto.SubmitIdentityRequest;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.OwnerIdentity;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.*;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.*;
import com.ltld.app.legacyvault.service.auditservice.AuditMailListener;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
import com.ltld.app.legacyvault.service.identityservice.OwnerIdentityService;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import com.ltld.app.legacyvault.utility.CitizenId;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KYC Owner trên MySQL thật: bật DMS lần đầu cần xác minh danh tính; OTP số điện thoại; lưu CCCD mã hoá;
 * một CCCD chỉ gắn với một tài khoản.
 */
@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.dms.scheduler.enabled=false",
        "app.kyc.sms-sandbox=true"
})
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OwnerIdentityIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @MockitoBean private EmailSender emailSender;
    @MockitoBean private ExecutorNotificationService executorNotificationService;
    @MockitoBean private AuditMailListener auditMailListener;

    @Autowired private OwnerIdentityService identityService;
    @Autowired private DmsConfigService dmsConfigService;
    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private ExecutorAssignmentRepository executorAssignmentRepository;
    @Autowired private OwnerIdentityRepository identityRepository;

    @Test
    void firstDmsEnable_requiresKyc_thenSucceedsAfterPhoneOtpAndCitizenId() {
        User owner = ownerWithVaultAndPrimary("Nguyễn Văn An");

        assertThatThrownBy(() -> dmsConfigService.upsertConfig(owner.getId(), dmsRequest()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.KYC_REQUIRED));

        PhoneOtpResponse otp = identityService.sendPhoneOtp(owner.getId(), "0912 345 678");
        identityService.verifyPhoneOtp(owner.getId(), otp.getSandboxCode());
        identityService.submit(owner.getId(), request("079201123456", "Nguyen Van An"));

        User reloaded = userRepository.findById(owner.getId()).orElseThrow();
        assertThat(reloaded.getKycLevel()).isEqualTo(KycLevel.BASIC);
        assertThat(reloaded.getPhoneNumber()).isEqualTo("+84912345678");
        OwnerIdentity identity = identityRepository.findByUserId(owner.getId()).orElseThrow();
        assertThat(identity.getCitizenIdEncrypted()).doesNotContain("079201123456");
        assertThat(identity.getCitizenIdMasked()).isEqualTo("0792••••3456");
        assertThat(identityService.matchDocumentId(owner.getId(), "079201123456")).isEqualTo(OwnerIdMatch.MATCHED);

        assertThat(dmsConfigService.upsertConfig(owner.getId(), dmsRequest()).created()).isTrue();
    }

    @Test
    void sameCitizenId_cannotBeLinkedToTwoAccounts() {
        User first = ownerWithVaultAndPrimary("Trần Thị Bình");
        verifyPhone(first, "0386123456");
        identityService.submit(first.getId(), request("001185000001", "Tran Thi Binh", LocalDate.of(1985, 1, 1),
                CitizenId.Gender.FEMALE));

        User second = ownerWithVaultAndPrimary("Trần Thị Bình");
        verifyPhone(second, "0386123457");
        assertThatThrownBy(() -> identityService.submit(second.getId(),
                request("001185000001", "Tran Thi Binh", LocalDate.of(1985, 1, 1), CitizenId.Gender.FEMALE)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CITIZEN_ID_TAKEN));
    }

    private void verifyPhone(User owner, String phone) {
        PhoneOtpResponse otp = identityService.sendPhoneOtp(owner.getId(), phone);
        identityService.verifyPhoneOtp(owner.getId(), otp.getSandboxCode());
    }

    private User ownerWithVaultAndPrimary(String name) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        User owner = userRepository.save(User.builder().email("owner-" + tag + "@example.com").fullName(name)
                .passwordHash("not-used").status(UserStatus.ACTIVE).build());
        User executor = userRepository.save(User.builder().email("exec-" + tag + "@example.com").fullName("Exec " + tag)
                .passwordHash("not-used").status(UserStatus.ACTIVE).build());
        vaultRepository.save(Vault.builder().owner(owner).name("Vault " + tag).build());
        executorAssignmentRepository.save(ExecutorAssignment.builder()
                .owner(owner).executorUser(executor).executorType(ExecutorType.PRIMARY).build());
        return owner;
    }

    private static SubmitIdentityRequest request(String citizenId, String name) {
        return request(citizenId, name, LocalDate.of(2001, 5, 2), CitizenId.Gender.MALE);
    }

    private static SubmitIdentityRequest request(String citizenId, String name, LocalDate dob, CitizenId.Gender gender) {
        SubmitIdentityRequest r = new SubmitIdentityRequest();
        r.setCitizenId(citizenId);
        r.setFullName(name);
        r.setDateOfBirth(dob);
        r.setGender(gender);
        r.setResidenceProvince(VnProvince.HO_CHI_MINH);
        r.setSource(KycSource.MANUAL);
        r.setDataConsent(true);
        return r;
    }

    private static DmsConfigRequest dmsRequest() {
        DmsConfigRequest r = new DmsConfigRequest();
        r.setCheckinIntervalDays(30);
        r.setGracePeriodDays(7);
        r.setReminderCount(3);
        return r;
    }
}
