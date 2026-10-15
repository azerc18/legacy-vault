package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-02 (Q26): kiểm câu JPQL existsLiveExecutorWithEmail trên MySQL thật
 * (so sánh enum status, join sang users và lower(email)).
 */
@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.dms.scheduler.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class ExecutorAssignmentRepositoryBeneficiaryCheckIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @Autowired
    private ExecutorAssignmentRepository executorAssignmentRepository;
    @Autowired
    private UserRepository userRepository;

    // Dữ liệu test tạo ra, để @AfterEach chỉ xóa đúng phần của mình.
    private final List<UUID> assignmentIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        executorAssignmentRepository.deleteAllById(assignmentIds);
        userRepository.deleteAllById(userIds);
        assignmentIds.clear();
        userIds.clear();
    }

    @Test
    void activeAndInactiveExecutorsOfOwner_areLive() {
        User owner = user("owner");
        User active = user("active");
        User inactive = user("inactive");
        assignment(owner, active, ExecutorType.PRIMARY, ExecutorStatus.ACTIVE);
        assignment(owner, inactive, ExecutorType.BACKUP, ExecutorStatus.INACTIVE);

        assertThat(executorAssignmentRepository.existsLiveExecutorWithEmail(owner.getId(), active.getEmail())).isTrue();
        assertThat(executorAssignmentRepository.existsLiveExecutorWithEmail(owner.getId(), inactive.getEmail())).isTrue();
    }

    @Test
    void revokedExecutor_isNotLive() {
        User owner = user("owner");
        User revoked = user("revoked");
        assignment(owner, revoked, ExecutorType.PRIMARY, ExecutorStatus.REVOKED);

        assertThat(executorAssignmentRepository.existsLiveExecutorWithEmail(owner.getId(), revoked.getEmail())).isFalse();
    }

    @Test
    void executorOfAnotherOwner_doesNotMatch() {
        User owner = user("owner");
        User otherOwner = user("other-owner");
        User executor = user("executor");
        assignment(otherOwner, executor, ExecutorType.PRIMARY, ExecutorStatus.ACTIVE);

        assertThat(executorAssignmentRepository.existsLiveExecutorWithEmail(owner.getId(), executor.getEmail())).isFalse();
    }

    @Test
    void mixedCaseEmailInUsers_matchesLowercaseParameter() {
        User owner = user("owner");
        String tag = UUID.randomUUID().toString().substring(0, 8);
        User executor = save("Mixed.Case-" + tag + "@Example.COM");
        assignment(owner, executor, ExecutorType.PRIMARY, ExecutorStatus.INACTIVE);

        assertThat(executorAssignmentRepository.existsLiveExecutorWithEmail(
                owner.getId(), ("mixed.case-" + tag + "@example.com"))).isTrue();
    }

    // ---------- helpers ----------

    private User user(String name) {
        return save(name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
    }

    private User save(String email) {
        User u = userRepository.save(User.builder()
                .email(email)
                .fullName(email)
                .passwordHash("not-used")
                .status(UserStatus.ACTIVE)
                .build());
        userIds.add(u.getId());
        return u;
    }

    private void assignment(User owner, User executor, ExecutorType type, ExecutorStatus status) {
        ExecutorAssignment a = executorAssignmentRepository.save(ExecutorAssignment.builder()
                .owner(owner)
                .executorUser(executor)
                .executorType(type)
                .status(status)
                .build());
        assignmentIds.add(a.getId());
    }
}
