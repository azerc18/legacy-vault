package com.ltld.app.legacyvault.service.mfaservice;

import com.ltld.app.legacyvault.dto.mfadto.MfaSetupResponse;
import com.ltld.app.legacyvault.entity.MfaBackupCode;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.MfaBackupCodeRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.utility.OtpHasher;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MfaServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock MfaBackupCodeRepository backupCodeRepository;
    @Mock CryptoService cryptoService;
    @Mock AuditLogService auditLogService;

    private final TotpService totpService = new TotpService();
    private final OtpHasher otpHasher = new OtpHasher();

    private MfaServiceImpl service;
    private User user;
    // Giả lập bảng mfa_backup_codes trong bộ nhớ
    private final List<MfaBackupCode> storedCodes = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        service = new MfaServiceImpl(userRepository, backupCodeRepository, totpService,
                cryptoService, otpHasher, auditLogService);

        user = User.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .fullName("Owner")
                .passwordHash("x")
                .build();

        lenient().when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        // "Mã hoá" giả: thêm tiền tố để kiểm tra secret không lưu dạng rõ
        lenient().when(cryptoService.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        lenient().when(cryptoService.decrypt(anyString()))
                .thenAnswer(inv -> ((String) inv.getArgument(0)).substring(4));

        lenient().when(backupCodeRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<MfaBackupCode> list = inv.getArgument(0);
            storedCodes.addAll(list);
            return list;
        });
        lenient().when(backupCodeRepository.findByUserIdAndUsedAtIsNull(user.getId()))
                .thenAnswer(inv -> storedCodes.stream().filter(c -> c.getUsedAt() == null).toList());
        lenient().doAnswer(inv -> { storedCodes.clear(); return null; })
                .when(backupCodeRepository).deleteByUserId(user.getId());
    }

    // ---------- helpers ----------

    private static String currentCode(String secret) throws Exception {
        long step = Instant.now().getEpochSecond() / 30;
        return new DefaultCodeGenerator().generate(secret, step);
    }

    /** setup + enable, trả về secret gốc để sinh mã trong test */
    private String enableMfa() throws Exception {
        MfaSetupResponse setup = service.setup(user.getId());
        service.enable(user.getId(), currentCode(setup.secret()));
        return setup.secret();
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    // ---------- setup ----------

    @Test
    void setup_storesEncryptedSecret_andDoesNotEnableYet() {
        MfaSetupResponse res = service.setup(user.getId());

        assertThat(res.secret()).isNotBlank();
        assertThat(res.otpauthUri()).startsWith("otpauth://totp/").contains("issuer=LegacyVault");
        assertThat(res.qrDataUri()).startsWith("data:image/png;base64,");
        assertThat(user.getMfaSecret()).isEqualTo("enc:" + res.secret());
        assertThat(user.isMfaEnabled()).isFalse();
    }

    @Test
    void setup_whenAlreadyEnabled_throwsConflict() throws Exception {
        enableMfa();

        assertThatThrownBy(() -> service.setup(user.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.CONFLICT));
    }

    // ---------- enable ----------

    @Test
    void enable_withCorrectCode_enablesAndReturns10UniqueBackupCodes() throws Exception {
        MfaSetupResponse setup = service.setup(user.getId());

        List<String> codes = service.enable(user.getId(), currentCode(setup.secret()));

        assertThat(user.isMfaEnabled()).isTrue();
        assertThat(user.getMfaLastUsedStep()).isNotNull();
        assertThat(codes).hasSize(10).doesNotHaveDuplicates()
                .allMatch(c -> c.matches("[A-Z2-9]{4}-[A-Z2-9]{4}"));
        // Chỉ lưu hash, không lưu bản gốc
        assertThat(storedCodes).hasSize(10)
                .noneMatch(c -> codes.contains(c.getCodeHash()));
        verify(auditLogService).success(AuditAction.MFA_ENABLED, user.getId(), user.getEmail());
    }

    @Test
    void enable_withWrongCode_throwsMfaInvalidCode_andAuditsFailure() {
        service.setup(user.getId());

        assertThatThrownBy(() -> service.enable(user.getId(), "000000"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.MFA_INVALID_CODE));

        assertThat(user.isMfaEnabled()).isFalse();
        assertThat(storedCodes).isEmpty();
        verify(auditLogService).failure(eq(AuditAction.MFA_VERIFY_FAILED), eq(user.getId()), eq(user.getEmail()), anyString());
    }

    @Test
    void enable_withoutSetup_throwsBadRequest() {
        assertThatThrownBy(() -> service.enable(user.getId(), "123456"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    // ---------- verifyCode ----------

    @Test
    void verifyCode_sameTotpCodeTwice_secondIsRejected() throws Exception {
        String secret = enableMfa();
        // Bỏ qua bước của mã đã dùng khi enable: lùi lastUsedStep để mã hiện tại hợp lệ 1 lần
        user.setMfaLastUsedStep(user.getMfaLastUsedStep() - 2);
        String code = currentCode(secret);

        assertThat(service.verifyCode(user, code)).isTrue();
        assertThat(service.verifyCode(user, code)).isFalse();
    }

    @Test
    void verifyCode_backupCode_worksOnlyOnce_andIgnoresDashAndCase() throws Exception {
        MfaSetupResponse setup = service.setup(user.getId());
        List<String> codes = service.enable(user.getId(), currentCode(setup.secret()));
        String backup = codes.get(0);
        String typedByUser = backup.replace("-", "").toLowerCase();

        assertThat(service.verifyCode(user, typedByUser)).isTrue();
        assertThat(service.verifyCode(user, backup)).isFalse();
        verify(auditLogService).success(AuditAction.MFA_BACKUP_CODE_USED, user.getId(), user.getEmail());
    }

    @Test
    void verifyCode_whenMfaDisabled_returnsFalse() {
        assertThat(service.verifyCode(user, "123456")).isFalse();
    }

    // ---------- disable ----------

    @Test
    void disable_withBackupCode_clearsSecretAndBackupCodes() throws Exception {
        MfaSetupResponse setup = service.setup(user.getId());
        List<String> codes = service.enable(user.getId(), currentCode(setup.secret()));

        service.disable(user.getId(), codes.get(1));

        assertThat(user.isMfaEnabled()).isFalse();
        assertThat(user.getMfaSecret()).isNull();
        assertThat(user.getMfaLastUsedStep()).isNull();
        assertThat(storedCodes).isEmpty();
        verify(auditLogService).success(AuditAction.MFA_DISABLED, user.getId(), user.getEmail());
    }

    @Test
    void disable_withWrongCode_throwsMfaInvalidCode_andKeepsMfaOn() throws Exception {
        enableMfa();

        assertThatThrownBy(() -> service.disable(user.getId(), "ZZZZ-ZZZZ"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.MFA_INVALID_CODE));

        assertThat(user.isMfaEnabled()).isTrue();
        assertThat(storedCodes).hasSize(10);
    }

    @Test
    void disable_whenNotEnabled_throwsConflict() {
        assertThatThrownBy(() -> service.disable(user.getId(), "123456"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.CONFLICT));
    }
}