package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.dto.forgotpassworddto.ResetPasswordRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.VerificationToken;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.TokenType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.InvalidOtpException;
import com.ltld.app.legacyvault.exception.OtpNotFoundException;
import com.ltld.app.legacyvault.exception.TooManyAttemptsException;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
import com.ltld.app.legacyvault.repository.RefreshTokenRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VerificationTokenRepository;
import com.ltld.app.legacyvault.service.authservice.AuthService;
import com.ltld.app.legacyvault.service.verificationservice.VerificationTokenService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers(disabledWithoutDocker = true)
class OtpConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @MockitoBean
    private EmailSender emailSender;            // không gửi mail thật

    @Autowired
    private VerificationTokenService verificationTokenService;
    @Autowired
    private AuthService authService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VerificationTokenRepository tokenRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String EMAIL = "race@example.com";
    private static final String OTP = "123456";

    private User user;
    private UUID tokenId;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email(EMAIL)
                .fullName("Race Tester")
                .passwordHash(passwordEncoder.encode("OldPass123"))
                .status(UserStatus.ACTIVE)
                .build());

        tokenId = tokenRepository.save(VerificationToken.builder()
                .otpCode(OTP)
                .expiresAt(Instant.now().plusSeconds(300))
                .createdAt(Instant.now())
                .type(TokenType.PASSWORD_RESET)
                .user(user)
                .build()).getId();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM audit_logs");
        refreshTokenRepository.deleteAll();
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- A. Version check cơ bản, không dùng thread ----------
    @Test
    void staleVersion_throwsOptimisticLock() {
        VerificationToken first = tokenRepository.findById(tokenId).orElseThrow();
        VerificationToken second = tokenRepository.findById(tokenId).orElseThrow();
        assertThat(first.getVersion()).isEqualTo(second.getVersion());

        first.setAttemptCount(1);
        tokenRepository.saveAndFlush(first);                 // version 0 -> 1

        second.setAttemptCount(1);                           // bản cũ, vẫn version 0
        assertThatThrownBy(() -> tokenRepository.saveAndFlush(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        VerificationToken reloaded = tokenRepository.findById(tokenId).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(1L);
        assertThat(reloaded.getAttemptCount()).isEqualTo(1);
    }

    // ---------- B. Đoán sai song song: không mất lượt đếm ----------
    @Test
    void concurrentWrongOtp_noLostUpdate() throws Exception {
        int threads = 10;

        List<Throwable> results = runConcurrently(threads, i -> () ->
                verificationTokenService.consumeOtp(user, TokenType.PASSWORD_RESET,
                        "000000", AuditAction.PASSWORD_RESET_FAILED));

        long invalid  = count(results, InvalidOtpException.class);
        long conflict = count(results, ObjectOptimisticLockingFailureException.class);
        long tooMany  = count(results, TooManyAttemptsException.class);

        // Mọi thread đều phải thất bại theo một trong 3 cách hợp lệ, không có lỗi lạ
        assertThat(invalid + conflict + tooMany).isEqualTo(threads);

        VerificationToken reloaded = tokenRepository.findById(tokenId).orElseThrow();
        // Bất biến quan trọng nhất: số lần client nhận "sai OTP" == số lần DB đếm được
        assertThat(reloaded.getAttemptCount()).isEqualTo((int) invalid);
        assertThat(reloaded.getAttemptCount()).isLessThanOrEqualTo(5);
        assertThat(reloaded.isUsed()).isFalse();

        System.out.printf("invalid=%d conflict=%d tooMany=%d%n", invalid, conflict, tooMany);
    }

    // ---------- C. OTP đúng gửi song song: chỉ 1 lần reset thành công ----------
    @Test
    void concurrentCorrectOtp_onlyOneResetSucceeds() throws Exception {
        int threads = 5;

        List<Throwable> results = runConcurrently(threads, i -> () ->
                authService.resetPassword(new ResetPasswordRequest(
                        EMAIL, OTP, "NewPass" + i, "NewPass" + i)));

        List<Integer> winners = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            Throwable t = results.get(i);
            if (t == null) {
                winners.add(i);
            } else {
                // Thua cuộc: xung đột version, hoặc đọc sau khi winner đã commit
                assertThat(t).isInstanceOfAny(
                        ObjectOptimisticLockingFailureException.class,
                        OtpNotFoundException.class);
            }
        }

        assertThat(winners).hasSize(1);

        User reloadedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPass" + winners.get(0),
                reloadedUser.getPasswordHash())).isTrue();

        VerificationToken reloadedToken = tokenRepository.findById(tokenId).orElseThrow();
        assertThat(reloadedToken.isUsed()).isTrue();
    }

    // ---------- helpers ----------
    /** Chạy n task "cùng lúc"; phần tử thứ i = null nếu task i thành công, ngược lại là exception. */
    private List<Throwable> runConcurrently(int n, IntFunction<Runnable> taskFactory) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            Runnable task = taskFactory.apply(i);
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();                 // tất cả cùng chờ ở vạch xuất phát
                try {
                    task.run();
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            }));
        }

        ready.await();
        start.countDown();                     // thả cho tất cả chạy cùng lúc

        List<Throwable> results = new ArrayList<>();
        for (Future<Throwable> f : futures) {
            results.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return results;
    }

    private long count(List<Throwable> results, Class<? extends Throwable> type) {
        return results.stream().filter(type::isInstance).count();
    }
}
