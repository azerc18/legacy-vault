package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.SendVerificationMessageRequest;
import com.ltld.app.legacyvault.dto.executordto.VerificationMessageResponse;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.VerificationExchangeMessage;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.VerificationRequestClosedException;
import com.ltld.app.legacyvault.exception.VerificationRequestNotFoundException;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VerificationExchangeMessageRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.dms.scheduler.enabled=false",
        "app.beneficiary.notify-retry.enabled=false",
        "app.storage.upload-dir=target/it-uploads/"
})
@Testcontainers(disabledWithoutDocker = true)
class VerificationExchangeIntegrationTest {

    private static final byte[] PDF = "%PDF-1.4 integration extra".getBytes(StandardCharsets.US_ASCII);

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @MockitoBean private EmailSender emailSender;

    @Autowired private VerificationExchangeService verificationExchangeService;
    @Autowired private VerificationExchangeMessageRepository verificationExchangeMessageRepository;
    @Autowired private LegalVerificationRequestRepository legalVerificationRequestRepository;
    @Autowired private ExecutorAssignmentRepository executorAssignmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private CryptoService cryptoService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User owner;
    private User executor;
    private User backupExecutor;
    private ExecutorAssignment primary;
    private ExecutorAssignment backup;
    private LegalVerificationRequest request;

    @BeforeEach
    void setUp() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        owner = user("owner-" + tag);
        executor = user("executor-" + tag);
        backupExecutor = user("backup-" + tag);
        primary = assignment(executor, ExecutorType.PRIMARY);
        backup = assignment(backupExecutor, ExecutorType.BACKUP);
        request = legalVerificationRequestRepository.save(LegalVerificationRequest.builder()
                .owner(owner).executor(executor).deathCertificateFileUrlEncrypted("/files/cert.pdf")
                .status(LegalVerificationStatus.PENDING).build());
    }

    @AfterEach
    void cleanUp() throws InterruptedException {
        List<UUID> userIds = List.of(owner.getId(), executor.getId(), backupExecutor.getId());
        awaitAuditRowsSettled();
        for (UUID id : userIds) {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE actor_id = UUID_TO_BIN(?)", id.toString());
        }
        jdbcTemplate.update("DELETE m FROM verification_exchange_messages m "
                + "JOIN legal_verification_requests r ON m.request_id = r.id "
                + "WHERE r.owner_id = UUID_TO_BIN(?)", owner.getId().toString());
        jdbcTemplate.update("DELETE FROM legal_verification_requests WHERE owner_id = UUID_TO_BIN(?)",
                owner.getId().toString());
        executorAssignmentRepository.deleteAll(executorAssignmentRepository.findAllByOwnerId(owner.getId()));
        userRepository.deleteAllById(userIds);
    }

    @Test
    void messageWithAttachment_isStoredEncrypted_andAppearsInHistory() throws Exception {
        VerificationMessageResponse sent = verificationExchangeService.send(primary.getId(), request.getId(),
                executor.getEmail(), new MockMultipartFile("file", "extra.pdf", "application/pdf", PDF),
                new SendVerificationMessageRequest("Bổ sung giấy xác nhận"));

        assertThat(sent.getAttachmentName()).isEqualTo("extra.pdf");
        VerificationExchangeMessage stored = verificationExchangeMessageRepository.findById(sent.getId()).orElseThrow();
        byte[] onDisk = fileStorageService.readFile(stored.getAttachmentPath());
        assertThat(onDisk).isNotEqualTo(PDF);
        assertThat(cryptoService.decryptBytes(onDisk)).isEqualTo(PDF);

        List<VerificationMessageResponse> history =
                verificationExchangeService.history(primary.getId(), request.getId(), executor.getEmail());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getContent()).isEqualTo("Bổ sung giấy xác nhận");
        assertThat(history.get(0).getSenderName()).isEqualTo(executor.getFullName());
    }

    @Test
    void otherExecutorOfSameOwner_cannotUseOrReadSomeoneElsesRequest() {
        assertThatThrownBy(() -> verificationExchangeService.send(backup.getId(), request.getId(),
                backupExecutor.getEmail(), null, new SendVerificationMessageRequest("xen vào")))
                .isInstanceOf(VerificationRequestNotFoundException.class);
        assertThatThrownBy(() -> verificationExchangeService.history(backup.getId(), request.getId(),
                backupExecutor.getEmail()))
                .isInstanceOf(VerificationRequestNotFoundException.class);

        assertThat(verificationExchangeMessageRepository.findHistory(request.getId())).isEmpty();
    }

    @Test
    void closedRequest_rejectsNewMessages_butKeepsHistoryReadable() {
        verificationExchangeService.send(primary.getId(), request.getId(), executor.getEmail(), null,
                new SendVerificationMessageRequest("trước khi bị từ chối"));
        request.setStatus(LegalVerificationStatus.REJECTED);
        legalVerificationRequestRepository.save(request);

        assertThatThrownBy(() -> verificationExchangeService.send(primary.getId(), request.getId(),
                executor.getEmail(), null, new SendVerificationMessageRequest("sau khi bị từ chối")))
                .isInstanceOf(VerificationRequestClosedException.class);

        assertThat(verificationExchangeService.history(primary.getId(), request.getId(), executor.getEmail()))
                .hasSize(1);
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

    private User user(String name) {
        return userRepository.save(User.builder()
                .email(name + "@example.com")
                .fullName(name)
                .passwordHash("not-used")
                .status(UserStatus.ACTIVE)
                .build());
    }

    private ExecutorAssignment assignment(User executorUser, ExecutorType type) {
        return executorAssignmentRepository.save(ExecutorAssignment.builder()
                .owner(owner)
                .executorUser(executorUser)
                .executorType(type)
                .status(ExecutorStatus.ACTIVE)
                .build());
    }
}
