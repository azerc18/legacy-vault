package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.dto.executordto.AssignExecutorRequest;
import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryRequest;
import com.ltld.app.legacyvault.dto.vaultdto.UpdateVaultRequest;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.exception.ExecutorManagementException;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.executorservice.ExecutorManagementService;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

// O2 (FR-02): chặn đổi người thụ hưởng sau khi DMS kích hoạt, và đóng write-skew Q26 giữa FR-02 và FR-06 (g2)
// khi Owner có DmsConfig: cả hai luồng khóa cùng dòng DmsConfig trước khi kiểm tra.
@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create",
        "app.dms.scheduler.enabled=false",
        "app.beneficiary.notify-retry.enabled=false",
        "app.beneficiary.vault-purge.enabled=false",
        "app.beneficiary.claim-expiry.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class BeneficiaryDmsGuardIntegrationTest {

    private static final int ROUNDS = 20;

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @MockitoBean
    private EmailSender emailSender;            // không gửi mail thật

    @Autowired
    private VaultService vaultService;
    @Autowired
    private ExecutorManagementService executorManagementService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VaultRepository vaultRepository;
    @Autowired
    private DmsConfigRepository dmsConfigRepository;
    @Autowired
    private ExecutorAssignmentRepository executorAssignmentRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private Vault vault;

    @BeforeEach
    void setUp() {
        owner = newUser("owner@example.com");
        vault = vaultRepository.save(Vault.builder().owner(owner).name("V").status(VaultStatus.ACTIVE).build());
    }

    @AfterEach
    void cleanUp() {
        // Con trước, cha sau (theo FK)
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM executor_assignments");
        jdbcTemplate.update("DELETE FROM dms_checkins");
        jdbcTemplate.update("DELETE FROM dms_configs");
        jdbcTemplate.update("DELETE FROM asset_allocations");
        jdbcTemplate.update("DELETE FROM vault_beneficiaries");
        jdbcTemplate.update("DELETE FROM vaults");
        jdbcTemplate.update("DELETE FROM user_roles");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void dmsTriggered_assignBeneficiary_returns409AndChangesNothing() {
        dmsConfigRepository.save(DmsConfig.builder()
                .owner(owner).status(DmsStatus.TRIGGERED).triggeredAt(LocalDateTime.now()).build());

        assertThatThrownBy(() -> vaultService.assignBeneficiary(owner.getId(), vault.getId(),
                beneficiaryRequest("ben@example.com")))
                .isInstanceOfSatisfying(DmsException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(vaultRepository.existsBeneficiaryWithEmail(owner.getId(), "ben@example.com")).isFalse();
        assertThat(auditLogRepository.existsByVaultIdAndAction(vault.getId(), AuditAction.BENEFICIARY_ASSIGNED)).isFalse();
        verifyNoInteractions(emailSender);
    }

    @Test
    void dmsTriggered_updateVault_blockedByGuardWith409() {
        // n3: Kiểm thử guard trong transaction thực tế - assertHandoverNotStarted MANDATORY hoạt động
        dmsConfigRepository.save(DmsConfig.builder()
                .owner(owner).status(DmsStatus.TRIGGERED).triggeredAt(LocalDateTime.now()).build());

        assertThatThrownBy(() -> vaultService.updateVault(vault.getId(), owner.getId(),
                new UpdateVaultRequest("Tên mới", "Mô tả mới")))
                .isInstanceOfSatisfying(DmsException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void noDmsConfig_assignBeneficiary_stillSucceeds() {
        // assertHandoverNotStarted là MANDATORY: chạy được tức là đã nằm trong transaction READ_COMMITTED của FR-02
        vaultService.assignBeneficiary(owner.getId(), vault.getId(), beneficiaryRequest("ben@example.com"));

        assertThat(vaultRepository.existsBeneficiaryWithEmail(owner.getId(), "ben@example.com")).isTrue();
        assertThat(auditLogRepository.existsByVaultIdAndAction(vault.getId(), AuditAction.BENEFICIARY_ASSIGNED)).isTrue();
    }

    // g2: cùng một người Y được gán song song làm người thụ hưởng (FR-02) và Executor PRIMARY (FR-06).
    // Mỗi vòng phải đúng một bên thắng, bên kia 409 theo Q26; không vòng nào Y giữ cả hai vai trò; không deadlock.
    @Test
    void concurrentBeneficiaryAndPrimaryExecutor_exactlyOneWinsEachRound() throws Exception {
        dmsConfigRepository.save(DmsConfig.builder().owner(owner).status(DmsStatus.ACTIVE).build());

        List<String> violations = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            String email = "y" + round + "@example.com";
            newUser(email);

            // confirmReplace=true: vòng trước đã để lại người khác ở cả hai vị trí
            List<Throwable> results = runConcurrently(List.of(
                    () -> vaultService.assignBeneficiary(owner.getId(), vault.getId(), beneficiaryRequest(email)),
                    () -> executorManagementService.assignExecutor(owner.getId(), ExecutorType.PRIMARY,
                            executorRequest(email))));

            long successes = results.stream().filter(t -> t == null).count();
            List<Throwable> unexpected = results.stream()
                    .filter(t -> t != null && !isQ26Conflict(t))
                    .toList();
            boolean isBeneficiary = vaultRepository.existsBeneficiaryWithEmail(owner.getId(), email);
            boolean isExecutor = executorAssignmentRepository.existsLiveExecutorWithEmail(owner.getId(), email);

            if (successes != 1 || !unexpected.isEmpty() || isBeneficiary == isExecutor) {
                violations.add("round " + round + ": successes=" + successes
                        + ", beneficiary=" + isBeneficiary + ", executor=" + isExecutor
                        + ", unexpected=" + unexpected.stream().map(t -> t.getClass().getSimpleName()
                        + ": " + t.getMessage()).toList());
            }
        }

        assertThat(violations).isEmpty();
    }

    private static boolean isQ26Conflict(Throwable t) {
        return (t instanceof VaultException ve && ve.getStatus() == HttpStatus.CONFLICT)
                || (t instanceof ExecutorManagementException ee && ee.getStatus() == HttpStatus.CONFLICT);
    }

    private User newUser(String email) {
        return userRepository.save(User.builder()
                .email(email).fullName("Tester").passwordHash("x").status(UserStatus.ACTIVE).build());
    }

    private static AssignBeneficiaryRequest beneficiaryRequest(String email) {
        AssignBeneficiaryRequest request = new AssignBeneficiaryRequest();
        request.setEmail(email);
        request.setConfirmReplace(true);
        return request;
    }

    private static AssignExecutorRequest executorRequest(String email) {
        AssignExecutorRequest request = new AssignExecutorRequest();
        request.setEmail(email);
        request.setConfirmReplace(true);
        return request;
    }

    // Chạy các task cùng lúc (cùng chờ ở vạch xuất phát); trả về exception của từng task, null nếu thành công
    private List<Throwable> runConcurrently(List<Runnable> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();

        for (Runnable task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    task.run();
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            }));
        }

        ready.await();
        start.countDown();

        List<Throwable> results = new ArrayList<>();
        for (Future<Throwable> f : futures) {
            results.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return results;
    }
}
