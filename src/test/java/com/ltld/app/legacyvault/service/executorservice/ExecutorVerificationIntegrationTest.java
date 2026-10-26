package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.CreateDeathVerificationRequest;
import com.ltld.app.legacyvault.dto.executordto.DeathVerificationResponse;
import com.ltld.app.legacyvault.dto.legalverificationdto.DocumentVerificationResponseDto;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.DuplicateVerificationRequestException;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.dmsservice.DmsActivationProcessor;
import com.ltld.app.legacyvault.service.legalverificationservice.LegalVerificationService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.dms.scheduler.enabled=false",
        "app.beneficiary.notify-retry.enabled=false",
        "app.beneficiary.vault-purge.enabled=false",
        "app.storage.upload-dir=target/it-uploads/"
})
@Testcontainers(disabledWithoutDocker = true)
class ExecutorVerificationIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @MockitoBean private EmailSender emailSender;

    @Autowired private ExecutorVerificationService executorVerificationService;
    @Autowired private LegalVerificationService legalVerificationService;
    @Autowired private UserRepository userRepository;
    @Autowired private DmsConfigRepository dmsConfigRepository;
    @Autowired private ExecutorAssignmentRepository executorAssignmentRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private LegalVerificationRequestRepository legalVerificationRequestRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DmsActivationProcessor dmsActivationProcessor;

    private User owner;
    private User primaryUser;
    private User backupUser;
    private User verifier;
    private ExecutorAssignment primary;
    private ExecutorAssignment backup;
    private Vault vault;

    @BeforeEach
    void setUp() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        owner = user("owner-" + tag);
        primaryUser = user("primary-" + tag);
        backupUser = user("backup-" + tag);
        verifier = user("verifier-" + tag);

        dmsConfigRepository.save(DmsConfig.builder().owner(owner).status(DmsStatus.TRIGGERED).build());
        primary = assignment(primaryUser, ExecutorType.PRIMARY);
        backup = assignment(backupUser, ExecutorType.BACKUP);
        vault = vaultRepository.save(Vault.builder().owner(owner).name("Vault " + tag)
                .status(VaultStatus.ACTIVE).build());
    }

    @AfterEach
    void cleanUp() throws InterruptedException {
        List<UUID> userIds = List.of(owner.getId(), primaryUser.getId(), backupUser.getId(), verifier.getId());
        awaitAuditRowsSettled();
        for (UUID id : userIds) {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE actor_id = UUID_TO_BIN(?)", id.toString());
        }
        jdbcTemplate.update("DELETE rv FROM verification_request_vaults rv "
                + "JOIN legal_verification_requests r ON rv.request_id = r.id "
                + "WHERE r.owner_id = UUID_TO_BIN(?)", owner.getId().toString());
        jdbcTemplate.update("DELETE FROM legal_verification_requests WHERE owner_id = UUID_TO_BIN(?)", owner.getId().toString());
        executorAssignmentRepository.deleteAll(executorAssignmentRepository.findAllByOwnerId(owner.getId()));
        vaultRepository.deleteAll(vaultRepository.findAll().stream()
                .filter(v -> v.getOwner().getId().equals(owner.getId()))
                .toList());
        dmsConfigRepository.findByOwnerId(owner.getId()).ifPresent(dmsConfigRepository::delete);
        userRepository.deleteAllById(userIds);
    }

    private void awaitAuditRowsSettled() throws InterruptedException {
        long previous = -1;
        for (int i = 0; i < 20; i++) {
            Long current = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Long.class);
            if (current != null && current == previous) {
                return;
            }
            previous = current == null ? -1 : current;
            Thread.sleep(200);
        }
    }

    @Test
    void submittedRequest_isAcceptedByDocumentVerification() {
        DeathVerificationResponse res = executorVerificationService.submit(primary.getId(), primaryUser.getEmail(),
                pdf(), new CreateDeathVerificationRequest(List.of(vault.getId()), "note"));

        LegalVerificationRequest stored = legalVerificationRequestRepository.findById(res.getId()).orElseThrow();
        assertThat(stored.getDeathCertificateFileUrlEncrypted()).endsWith(".pdf");

        // T-3: phải View trước khi Check
        legalVerificationService.getDocument(res.getId(), verifier.getId());
        DocumentVerificationResponseDto verification =
                legalVerificationService.verifyDocument(res.getId(), verifier.getId());
        assertThat(verification.isValid()).isTrue();
    }

    @Test
    void primaryAndBackupSubmittingSameVaultConcurrently_createExactlyOneRequest() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<DeathVerificationResponse>> futures = new ArrayList<>();
            futures.add(pool.submit(submitAs(primary, primaryUser, ready, go)));
            futures.add(pool.submit(submitAs(backup, backupUser, ready, go)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int succeeded = 0;
            int duplicates = 0;
            for (Future<DeathVerificationResponse> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    succeeded++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(DuplicateVerificationRequestException.class);
                    duplicates++;
                }
            }

            assertThat(succeeded).isEqualTo(1);
            assertThat(duplicates).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        Integer requests = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM legal_verification_requests WHERE owner_id = UUID_TO_BIN(?)", Integer.class,
                owner.getId().toString());
        assertThat(requests).isEqualTo(1);
        assertThat(legalVerificationRequestRepository.findAll().stream()
                .filter(r -> r.getOwner().getId().equals(owner.getId()))
                .allMatch(r -> r.getStatus() == LegalVerificationStatus.PENDING)).isTrue();
    }

    @Test
    void vaultWithOpenRequest_blocksNewSubmission_butOtherVaultIsAccepted() {
        executorVerificationService.submit(primary.getId(), primaryUser.getEmail(),
                pdf(), new CreateDeathVerificationRequest(List.of(vault.getId()), null));

        Vault other = vaultRepository.save(Vault.builder().owner(owner).name("Other")
                .status(VaultStatus.ACTIVE).build());
        assertThrows(DuplicateVerificationRequestException.class,
                () -> executorVerificationService.submit(backup.getId(), backupUser.getEmail(),
                        pdf(), new CreateDeathVerificationRequest(List.of(vault.getId(), other.getId()), null)));

        DeathVerificationResponse second = executorVerificationService.submit(backup.getId(), backupUser.getEmail(),
                pdf(), new CreateDeathVerificationRequest(List.of(other.getId()), null));
        assertThat(second.getVaultIds()).containsExactly(other.getId());
    }

    @Test
    void primarySubmittingBeforeAcknowledging_isNotRevokedByBr03AfterWaitDays() {
        LocalDateTime activatedAt = LocalDateTime.now().minusDays(20);
        primary.setActivatedAt(activatedAt);
        primary = executorAssignmentRepository.save(primary);
        backup.setStatus(ExecutorStatus.INACTIVE);
        backup = executorAssignmentRepository.save(backup);

        executorVerificationService.submit(primary.getId(), primaryUser.getEmail(),
                pdf(), new CreateDeathVerificationRequest(List.of(vault.getId()), null));

        UUID configId = dmsConfigRepository.findByOwnerId(owner.getId()).orElseThrow().getId();
        dmsActivationProcessor.escalateIfPrimaryUnresponsive(configId, LocalDateTime.now());

        ExecutorAssignment reloadedPrimary = executorAssignmentRepository.findById(primary.getId()).orElseThrow();
        assertThat(reloadedPrimary.getAcknowledgedAt()).isNotNull();
        assertThat(reloadedPrimary.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(executorAssignmentRepository.findById(backup.getId()).orElseThrow().getStatus())
                .isEqualTo(ExecutorStatus.INACTIVE);
    }

    private Callable<DeathVerificationResponse> submitAs(ExecutorAssignment assignment, User executorUser,
                                                         CountDownLatch ready, CountDownLatch go) {
        return () -> {
            ready.countDown();
            go.await();
            return executorVerificationService.submit(assignment.getId(), executorUser.getEmail(), pdf(),
                    new CreateDeathVerificationRequest(List.of(vault.getId()), null));
        };
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "death-cert.pdf", "application/pdf",
                "%PDF-1.4 integration test".getBytes(StandardCharsets.US_ASCII));
    }

    private User user(String name) {
        return userRepository.save(User.builder()
                .email(name + "@example.com")
                .fullName(name)
                .passwordHash("not-used")
                .status(UserStatus.ACTIVE)
                .build());
    }

    private ExecutorAssignment assignment(User executor, ExecutorType type) {
        return executorAssignmentRepository.save(ExecutorAssignment.builder()
                .owner(owner)
                .executorUser(executor)
                .executorType(type)
                .status(ExecutorStatus.ACTIVE)
                .build());
    }
}
