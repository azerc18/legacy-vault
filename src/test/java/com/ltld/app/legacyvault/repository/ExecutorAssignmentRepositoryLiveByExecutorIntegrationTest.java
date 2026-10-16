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

/** B1: kiểm câu JPQL findLiveByExecutorUserId / findLiveByExecutorEmail trên MySQL thật. */
@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.dms.scheduler.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class ExecutorAssignmentRepositoryLiveByExecutorIntegrationTest {

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
    void byUserId_returnsLiveAssignmentsOfSeveralOwners_excludingRevokedAndOthers() {
        User ownerA = user("owner-a");
        User ownerB = user("owner-b");
        User ownerC = user("owner-c");
        User me = user("me");
        User other = user("other");
        assignment(ownerA, me, ExecutorType.PRIMARY, ExecutorStatus.ACTIVE);
        assignment(ownerB, me, ExecutorType.BACKUP, ExecutorStatus.INACTIVE);
        assignment(ownerC, me, ExecutorType.PRIMARY, ExecutorStatus.REVOKED);
        assignment(ownerA, other, ExecutorType.BACKUP, ExecutorStatus.ACTIVE);

        List<ExecutorAssignment> result = executorAssignmentRepository.findLiveByExecutorUserId(me.getId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(a -> a.getOwner().getId())
                .containsExactlyInAnyOrder(ownerA.getId(), ownerB.getId());
    }

    @Test
    void byEmail_mixedCaseEmailInUsers_matchesLowercaseParameter() {
        User owner = user("owner");
        String tag = UUID.randomUUID().toString().substring(0, 8);
        User me = save("Mixed.Case-" + tag + "@Example.COM");
        assignment(owner, me, ExecutorType.PRIMARY, ExecutorStatus.INACTIVE);

        assertThat(executorAssignmentRepository.findLiveByExecutorEmail("mixed.case-" + tag + "@example.com"))
                .hasSize(1);
    }

    @Test
    void byEmail_revokedOnly_returnsEmpty() {
        User owner = user("owner");
        User me = user("me");
        assignment(owner, me, ExecutorType.PRIMARY, ExecutorStatus.REVOKED);

        assertThat(executorAssignmentRepository.findLiveByExecutorEmail(me.getEmail())).isEmpty();
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
