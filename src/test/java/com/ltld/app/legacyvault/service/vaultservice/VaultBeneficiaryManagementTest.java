package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.dto.vaultdto.AddBeneficiaryRequest;
import com.ltld.app.legacyvault.dto.vaultdto.UpdateBeneficiaryRequest;
import com.ltld.app.legacyvault.dto.vaultdto.VaultBeneficiaryResponse;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.enums.PlanFeature;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.PlanLimitReachedException;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.AssetAllocationRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.paymentservice.EntitlementService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VaultServiceImpl – N2-owner: thêm/xóa người thụ hưởng")
class VaultBeneficiaryManagementTest {

    @Mock private VaultRepository vaultRepository;
    @Mock private DigitalAssetRepository digitalAssetRepository;
    @Mock private LegalDocumentRepository legalDocumentRepository;
    @Mock private UserRepository userRepository;
    @Mock private CryptoService cryptoService;
    @Mock private FileStorageService fileStorageService;
    @Mock private AuditLogService auditLogService;
    @Mock private EmailSender emailSender;
    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private VaultModificationGuard vaultModificationGuard;
    @Mock private DmsConfigService dmsConfigService;
    @Mock private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Mock private AssetAllocationRepository assetAllocationRepository;
    @Mock private EntitlementService entitlementService;

    @InjectMocks private VaultServiceImpl vaultService;

    private UUID ownerId;
    private UUID vaultId;
    private User owner;
    private Vault vault;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        vaultId = UUID.randomUUID();
        owner = User.builder().id(ownerId).email("owner@test.com").fullName("Owner").build();
        vault = Vault.builder()
                .id(vaultId)
                .name("My Vault")
                .owner(owner)
                .status(VaultStatus.ACTIVE)
                .build();
    }

    // ===== helpers =====

    private void lockVault() {
        when(vaultRepository.findByIdForUpdate(vaultId)).thenReturn(Optional.of(vault));
    }

    private VaultBeneficiary row(String email, User user) {
        return VaultBeneficiary.builder()
                .id(UUID.randomUUID())
                .vault(vault)
                .user(user)
                .email(email)
                .status(user == null ? BeneficiaryInviteStatus.PENDING_REGISTRATION
                        : BeneficiaryInviteStatus.INVITED)
                .build();
    }

    private static AddBeneficiaryRequest addRequest(String email) {
        AddBeneficiaryRequest r = new AddBeneficiaryRequest();
        r.setEmail(email);
        return r;
    }

    private void stubSaveAssignsId() {
        when(vaultBeneficiaryRepository.save(any(VaultBeneficiary.class))).thenAnswer(inv -> {
            VaultBeneficiary b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private static void assertStatus(ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call).isInstanceOfSatisfying(VaultException.class,
                e -> assertThat(e.getStatus()).isEqualTo(status));
    }

    // ===== addBeneficiary =====

    @Test
    @DisplayName("add: trùng email Owner (không phân biệt hoa thường, có khoảng trắng) → BENEFICIARY_SELF")
    void addBeneficiary_ownerEmail_throwsSelf() {
        lockVault();

        assertCode(() -> vaultService.addBeneficiary(ownerId, vaultId, addRequest("  OWNER@test.com ")),
                ErrorCode.BENEFICIARY_SELF);
        verify(vaultBeneficiaryRepository, never()).save(any(VaultBeneficiary.class));
    }

    @Test
    @DisplayName("add: trùng Executor còn hiệu lực → BENEFICIARY_CONFLICT")
    void addBeneficiary_executorEmail_throwsConflict() {
        lockVault();
        when(executorAssignmentRepository.existsLiveExecutorWithEmail(ownerId, "exec@test.com")).thenReturn(true);

        assertCode(() -> vaultService.addBeneficiary(ownerId, vaultId, addRequest("exec@test.com")),
                ErrorCode.BENEFICIARY_CONFLICT);
        verify(vaultBeneficiaryRepository, never()).save(any(VaultBeneficiary.class));
    }

    @Test
    @DisplayName("add: email đã là người thụ hưởng của vault → BENEFICIARY_DUPLICATE")
    void addBeneficiary_duplicateEmail_throwsDuplicate() {
        lockVault();
        when(vaultBeneficiaryRepository.existsByVaultIdAndEmail(vaultId, "dup@test.com")).thenReturn(true);

        assertCode(() -> vaultService.addBeneficiary(ownerId, vaultId, addRequest("dup@test.com")),
                ErrorCode.BENEFICIARY_DUPLICATE);
        verify(vaultBeneficiaryRepository, never()).save(any(VaultBeneficiary.class));
    }

    @Test
    @DisplayName("add: guard từ chối (vault không ACTIVE / đang khóa) → lỗi của guard, không lưu gì")
    void addBeneficiary_guardRejects_throws() {
        lockVault();
        doThrow(new VaultException("Cannot modify vault that is not in active state", HttpStatus.BAD_REQUEST))
                .when(vaultModificationGuard).ensureModifiable(vault);

        assertStatus(() -> vaultService.addBeneficiary(ownerId, vaultId, addRequest("a@test.com")),
                HttpStatus.BAD_REQUEST);
        verify(vaultBeneficiaryRepository, never()).save(any(VaultBeneficiary.class));
    }

    @Test
    @DisplayName("add: không phải chủ vault → 404 và ghi audit từ chối")
    void addBeneficiary_notOwner_throws404() {
        vault.setOwner(User.builder().id(UUID.randomUUID()).email("other@test.com").build());
        lockVault();

        assertStatus(() -> vaultService.addBeneficiary(ownerId, vaultId, addRequest("a@test.com")),
                HttpStatus.NOT_FOUND);
        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_ASSIGN_DENIED), eq(AuditResult.FAILURE),
                any(), any(), any(), any(), any(), any());
        verify(vaultBeneficiaryRepository, never()).save(any(VaultBeneficiary.class));
    }

    @Test
    @DisplayName("add: người đầu tiên, chưa có tài khoản → PENDING_REGISTRATION, đồng bộ cột cũ, gửi thư mời")
    void addBeneficiary_firstPending_syncsLegacyAndInvites() {
        lockVault();
        when(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId)).thenReturn(List.of());
        when(userRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        stubSaveAssignsId();

        AddBeneficiaryRequest req = addRequest("New@Test.com");
        req.setFullName("Nguyen An");
        req.setRelationship("Con");

        VaultBeneficiaryResponse res = vaultService.addBeneficiary(ownerId, vaultId, req);

        assertThat(res.id()).isNotNull();
        assertThat(res.email()).isEqualTo("new@test.com");
        assertThat(res.fullName()).isEqualTo("Nguyen An");
        assertThat(res.relationship()).isEqualTo("Con");
        assertThat(res.status()).isEqualTo(BeneficiaryInviteStatus.PENDING_REGISTRATION);

        verify(dmsConfigService).assertHandoverNotStarted(ownerId);
        verify(entitlementService).assertCanAddBeneficiaries(ownerId, 1);
        // cột cũ mirror người đầu tiên
        assertThat(vault.getBeneficiary()).isNull();
        assertThat(vault.getBeneficiaryEmail()).isEqualTo("new@test.com");
        verify(vaultRepository).save(vault);

        verify(emailSender).sendBeneficiaryInvitation("new@test.com", "Owner", "My Vault");
        verify(emailSender).sendBeneficiaryAssignmentConfirmation("owner@test.com", "My Vault", "new@test.com", true);
        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_ASSIGNED), eq(AuditResult.SUCCESS),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("add: người thứ hai, đã có tài khoản → INVITED, KHÔNG đổi cột cũ, đếm giới hạn gói = 2")
    void addBeneficiary_second_doesNotTouchLegacy() {
        VaultBeneficiary first = row("first@test.com", null);
        vault.setBeneficiaryEmail("first@test.com");
        User existing = User.builder().id(UUID.randomUUID()).email("second@test.com")
                .status(UserStatus.ACTIVE).build();

        lockVault();
        when(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId)).thenReturn(List.of(first));
        when(userRepository.findByEmail("second@test.com")).thenReturn(Optional.of(existing));
        stubSaveAssignsId();

        VaultBeneficiaryResponse res = vaultService.addBeneficiary(ownerId, vaultId, addRequest("second@test.com"));

        assertThat(res.status()).isEqualTo(BeneficiaryInviteStatus.INVITED);
        verify(entitlementService).assertCanAddBeneficiaries(ownerId, 2);
        assertThat(vault.getBeneficiaryEmail()).isEqualTo("first@test.com");
        verify(vaultRepository, never()).save(any(Vault.class));
        verify(emailSender).sendBeneficiaryAssignedNotice("second@test.com", "Owner", "My Vault");
    }

    // ===== removeBeneficiary(ownerId, vaultId, beneficiaryId) =====

    @Test
    @DisplayName("remove: người cuối cùng → LAST_BENEFICIARY")
    void removeBeneficiary_last_throws() {
        VaultBeneficiary only = row("a@test.com", null);
        lockVault();
        when(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId)).thenReturn(List.of(only));

        assertCode(() -> vaultService.removeBeneficiary(ownerId, vaultId, only.getId()),
                ErrorCode.LAST_BENEFICIARY);
        verify(vaultBeneficiaryRepository, never()).delete(any(VaultBeneficiary.class));
    }

    @Test
    @DisplayName("remove: id không thuộc vault → 404")
    void removeBeneficiary_unknownId_throws404() {
        lockVault();
        when(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId))
                .thenReturn(List.of(row("a@test.com", null), row("b@test.com", null)));

        assertStatus(() -> vaultService.removeBeneficiary(ownerId, vaultId, UUID.randomUUID()),
                HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("remove: đang là người nhận chính của một tài sản → BENEFICIARY_IN_USE, không xóa gì")
    void removeBeneficiary_inUse_throws() {
        VaultBeneficiary a = row("a@test.com", null);
        VaultBeneficiary b = row("b@test.com", null);
        lockVault();
        when(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId)).thenReturn(List.of(a, b));
        when(assetAllocationRepository.existsByBeneficiaryId(b.getId())).thenReturn(true);

        assertCode(() -> vaultService.removeBeneficiary(ownerId, vaultId, b.getId()),
                ErrorCode.BENEFICIARY_IN_USE);
        verify(assetAllocationRepository, never()).clearContingentByBeneficiaryId(any());
        verify(vaultBeneficiaryRepository, never()).delete(any(VaultBeneficiary.class));
    }

    @Test
    @DisplayName("remove: guard từ chối (vault không ACTIVE / đang khóa) → lỗi của guard, không xóa gì")
    void removeBeneficiary_guardRejects_throws() {
        lockVault();
        doThrow(new VaultException("Cannot modify vault that is not in active state", HttpStatus.BAD_REQUEST))
                .when(vaultModificationGuard).ensureModifiable(vault);

        assertStatus(() -> vaultService.removeBeneficiary(ownerId, vaultId, UUID.randomUUID()),
                HttpStatus.BAD_REQUEST);
        verify(vaultBeneficiaryRepository, never()).delete(any(VaultBeneficiary.class));
    }

    @Test
    @DisplayName("remove: xóa người cũ nhất, người kế tiếp chưa có tài khoản → cột cũ chuyển sang email người kế tiếp")
    void removeBeneficiary_oldest_syncsLegacyToNextEmail() {
        VaultBeneficiary a = row("a@test.com", null);
        VaultBeneficiary b = row("b@test.com", null);
        vault.setBeneficiaryEmail("a@test.com");
        lockVault();
        when(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId)).thenReturn(List.of(a, b));

        vaultService.removeBeneficiary(ownerId, vaultId, a.getId());

        verify(assetAllocationRepository).clearContingentByBeneficiaryId(a.getId());
        verify(vaultBeneficiaryRepository).delete(a);
        assertThat(vault.getBeneficiary()).isNull();
        assertThat(vault.getBeneficiaryEmail()).isEqualTo("b@test.com");
        verify(vaultRepository).save(vault);
        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_REMOVED), eq(AuditResult.SUCCESS),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("remove: xóa người cũ nhất, người kế tiếp đã có tài khoản → cột cũ trỏ tới user, bỏ email chờ")
    void removeBeneficiary_oldest_syncsLegacyToNextUser() {
        User bUser = User.builder().id(UUID.randomUUID()).email("b@test.com").status(UserStatus.ACTIVE).build();
        VaultBeneficiary a = row("a@test.com", null);
        VaultBeneficiary b = row("b@test.com", bUser);
        vault.setBeneficiaryEmail("a@test.com");
        lockVault();
        when(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId)).thenReturn(List.of(a, b));

        vaultService.removeBeneficiary(ownerId, vaultId, a.getId());

        assertThat(vault.getBeneficiary()).isSameAs(bUser);
        assertThat(vault.getBeneficiaryEmail()).isNull();
    }

    @Test
    @DisplayName("remove: xóa người không phải cũ nhất → KHÔNG đổi cột cũ")
    void removeBeneficiary_notOldest_leavesLegacy() {
        VaultBeneficiary a = row("a@test.com", null);
        VaultBeneficiary b = row("b@test.com", null);
        vault.setBeneficiaryEmail("a@test.com");
        lockVault();
        when(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId)).thenReturn(List.of(a, b));

        vaultService.removeBeneficiary(ownerId, vaultId, b.getId());

        verify(vaultBeneficiaryRepository).delete(b);
        assertThat(vault.getBeneficiaryEmail()).isEqualTo("a@test.com");
        verify(vaultRepository, never()).save(any(Vault.class));
    }

    @Test
    @DisplayName("add: vượt giới hạn gói → PLAN_LIMIT_REACHED, không lưu, không audit, không gửi mail")
    void addBeneficiary_planLimit_throwsAndSavesNothing() {
        VaultBeneficiary first = row("first@test.com", null);
        lockVault();
        when(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId)).thenReturn(List.of(first));
        doThrow(new PlanLimitReachedException(PlanFeature.BENEFICIARY_LIMIT, "Beneficiary limit reached"))
                .when(entitlementService).assertCanAddBeneficiaries(ownerId, 2);

        assertCode(() -> vaultService.addBeneficiary(ownerId, vaultId, addRequest("second@test.com")),
                ErrorCode.PLAN_LIMIT_REACHED);
        verify(vaultBeneficiaryRepository, never()).save(any(VaultBeneficiary.class));
        verify(vaultRepository, never()).save(any(Vault.class));
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any(), any());
        verify(emailSender, never()).sendBeneficiaryInvitation(any(), any(), any());
        verify(emailSender, never()).sendBeneficiaryAssignedNotice(any(), any(), any());
    }

    @Test
    @DisplayName("remove: gửi mail cho người bị xóa (sau commit)")
    void removeBeneficiary_sendsRemovedNotice() {
        VaultBeneficiary a = row("a@test.com", null);
        VaultBeneficiary b = row("b@test.com", null);
        lockVault();
        when(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId)).thenReturn(List.of(a, b));

        vaultService.removeBeneficiary(ownerId, vaultId, b.getId());

        verify(emailSender).sendBeneficiaryRemovedNotice("b@test.com", "Owner", "My Vault");
    }

    @Test
    @DisplayName("remove: bị chặn (đang được phân bổ) → không gửi mail")
    void removeBeneficiary_blocked_noMail() {
        VaultBeneficiary a = row("a@test.com", null);
        VaultBeneficiary b = row("b@test.com", null);
        lockVault();
        when(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId)).thenReturn(List.of(a, b));
        when(assetAllocationRepository.existsByBeneficiaryId(b.getId())).thenReturn(true);

        assertCode(() -> vaultService.removeBeneficiary(ownerId, vaultId, b.getId()),
                ErrorCode.BENEFICIARY_IN_USE);
        verify(emailSender, never()).sendBeneficiaryRemovedNotice(any(), any(), any());
    }

    @Test
    @DisplayName("remove: audit ghi số tài sản bị bỏ vai trò dự phòng (contingentCleared)")
    void removeBeneficiary_auditsContingentCleared() {
        VaultBeneficiary a = row("a@test.com", null);
        VaultBeneficiary b = row("b@test.com", null);
        lockVault();
        when(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId)).thenReturn(List.of(a, b));
        when(assetAllocationRepository.clearContingentByBeneficiaryId(b.getId())).thenReturn(2);

        vaultService.removeBeneficiary(ownerId, vaultId, b.getId());

        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_REMOVED), eq(AuditResult.SUCCESS),
                any(), any(), any(), any(), any(),
                eq("beneficiaryRowId=" + b.getId() + ", contingentCleared=2"));
    }

    @Test
    @DisplayName("remove: không phải chủ vault → 404 và ghi audit từ chối")
    void removeBeneficiary_notOwner_throws404() {
        vault.setOwner(User.builder().id(UUID.randomUUID()).email("other@test.com").build());
        lockVault();

        assertStatus(() -> vaultService.removeBeneficiary(ownerId, vaultId, UUID.randomUUID()),
                HttpStatus.NOT_FOUND);
        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_REMOVE_DENIED), eq(AuditResult.FAILURE),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("remove: DMS đã kích hoạt → DmsException, chưa khóa vault")
    void removeBeneficiary_dmsTriggered_propagates() {
        doThrow(new DmsException("The Dead Man's Switch has started the handover process and cannot be changed.",
                HttpStatus.CONFLICT)).when(dmsConfigService).assertHandoverNotStarted(ownerId);

        assertThatThrownBy(() -> vaultService.removeBeneficiary(ownerId, vaultId, UUID.randomUUID()))
                .isInstanceOf(DmsException.class);
        verify(vaultRepository, never()).findByIdForUpdate(any());
    }

    // ===== updateBeneficiary =====

    private static UpdateBeneficiaryRequest updateRequest(String fullName, String relationship) {
        UpdateBeneficiaryRequest r = new UpdateBeneficiaryRequest();
        r.setFullName(fullName);
        r.setRelationship(relationship);
        return r;
    }

    @Test
    @DisplayName("update: sửa tên + quan hệ (trim, rỗng → null), giữ email/trạng thái, ghi audit")
    void updateBeneficiary_updatesNameAndRelationship() {
        VaultBeneficiary only = row("a@test.com", null);
        only.setFullName("Old");
        only.setRelationship("Bạn");
        lockVault();
        when(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId)).thenReturn(List.of(only));

        VaultBeneficiaryResponse res = vaultService.updateBeneficiary(ownerId, vaultId, only.getId(),
                updateRequest("  Nguyễn Văn A  ", "   "));

        assertThat(res.fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(res.relationship()).isNull();
        assertThat(res.email()).isEqualTo("a@test.com");
        assertThat(res.status()).isEqualTo(BeneficiaryInviteStatus.PENDING_REGISTRATION);
        verify(vaultBeneficiaryRepository).save(only);
        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_UPDATED), eq(AuditResult.SUCCESS),
                any(), any(), any(), any(), any(), eq("beneficiaryRowId=" + only.getId()));
        verify(emailSender, never()).sendBeneficiaryRemovedNotice(any(), any(), any());
    }

    @Test
    @DisplayName("update: id không thuộc vault → 404")
    void updateBeneficiary_unknownId_throws404() {
        lockVault();
        when(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId))
                .thenReturn(List.of(row("a@test.com", null)));

        assertStatus(() -> vaultService.updateBeneficiary(ownerId, vaultId, UUID.randomUUID(),
                updateRequest("X", null)), HttpStatus.NOT_FOUND);
        verify(vaultBeneficiaryRepository, never()).save(any(VaultBeneficiary.class));
    }

    @Test
    @DisplayName("update: không phải chủ vault → 404 và ghi audit từ chối")
    void updateBeneficiary_notOwner_throws404() {
        vault.setOwner(User.builder().id(UUID.randomUUID()).email("other@test.com").build());
        lockVault();

        assertStatus(() -> vaultService.updateBeneficiary(ownerId, vaultId, UUID.randomUUID(),
                updateRequest("X", null)), HttpStatus.NOT_FOUND);
        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_UPDATE_DENIED), eq(AuditResult.FAILURE),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("update: guard từ chối (vault đang khóa) → lỗi của guard, không lưu")
    void updateBeneficiary_guardRejects_throws() {
        lockVault();
        doThrow(new VaultException("Cannot modify vault that is not in active state", HttpStatus.BAD_REQUEST))
                .when(vaultModificationGuard).ensureModifiable(vault);

        assertStatus(() -> vaultService.updateBeneficiary(ownerId, vaultId, UUID.randomUUID(),
                updateRequest("X", null)), HttpStatus.BAD_REQUEST);
        verify(vaultBeneficiaryRepository, never()).save(any(VaultBeneficiary.class));
    }

    @Test
    @DisplayName("update: DMS đã kích hoạt → DmsException, chưa khóa vault")
    void updateBeneficiary_dmsTriggered_propagates() {
        doThrow(new DmsException("The Dead Man's Switch has started the handover process and cannot be changed.",
                HttpStatus.CONFLICT)).when(dmsConfigService).assertHandoverNotStarted(ownerId);

        assertThatThrownBy(() -> vaultService.updateBeneficiary(ownerId, vaultId, UUID.randomUUID(),
                updateRequest("X", null))).isInstanceOf(DmsException.class);
        verify(vaultRepository, never()).findByIdForUpdate(any());
    }
}
