package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryRequest;
import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryResponse;
import com.ltld.app.legacyvault.dto.vaultdto.CreateVaultRequest;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.dto.vaultdto.AdminVaultDetailResponse;
import com.ltld.app.legacyvault.dto.vaultdto.UpdateVaultRequest;
import com.ltld.app.legacyvault.dto.vaultdto.VaultDetailResponse;
import com.ltld.app.legacyvault.dto.vaultdto.VaultResponse;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ltld.app.legacyvault.enums.AuditResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VaultServiceImplTest {

    // 1. Mock (Làm giả) các phụ thuộc (Database, Crypto) để không đụng tới DB thật khi test
    @Mock
    private VaultRepository vaultRepository;
    @Mock
    private DigitalAssetRepository digitalAssetRepository;
    @Mock
    private LegalDocumentRepository legalDocumentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CryptoService cryptoService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private com.ltld.app.legacyvault.service.auditservice.AuditLogService auditLogService;
    @Mock
    private EmailSender emailSender;
    @Mock
    private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock
    private VaultModificationGuard vaultModificationGuard;

    // 2. Bơm các class giả ở trên vào Class thật mà ta đang muốn test
    @InjectMocks
    private VaultServiceImpl vaultService;

    private User mockOwner;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        mockOwner = new User();
        mockOwner.setId(ownerId);
        mockOwner.setEmail("owner@test.com");
    }

    // Kịch bản 1: Tạo Vault và tài sản số thành công (Mô phỏng FR-01)
    @Test
    void createVault_Success() throws Exception {
        // Chuẩn bị dữ liệu gửi lên (Arrange)
        CreateVaultRequest request = new CreateVaultRequest();
        request.setName("My Secret Vault");

        CreateVaultRequest.DigitalAssetDto assetDto = new CreateVaultRequest.DigitalAssetDto();
        assetDto.setAssetType(AssetType.BANK_ACCOUNT);
        assetDto.setSecret("my-password");
        request.setAssets(List.of(assetDto));

        Vault savedVault = new Vault();
        savedVault.setId(UUID.randomUUID());
        savedVault.setName("My Secret Vault");
        savedVault.setStatus(VaultStatus.ACTIVE);
        savedVault.setOwner(mockOwner);

        // Kịch bản giả lập (Khi code gọi hàm A thì trả về B)
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(mockOwner));
        when(vaultRepository.save(any(Vault.class))).thenReturn(savedVault);
        when(cryptoService.encrypt("my-password")).thenReturn("encrypted-password"); // Trọng tâm KT

        // Gọi hàm thực tế (Act)
        Vault result = vaultService.createVault(ownerId, request);

        // Kiểm tra kết quả (Assert)
        assertNotNull(result);
        assertEquals("My Secret Vault", result.getName());
        assertEquals(VaultStatus.ACTIVE, result.getStatus());

        // Đảm bảo hàm save và hàm encrypt đã chạy ngầm đúng 1 lần
        verify(vaultRepository, times(1)).save(any(Vault.class));
        verify(digitalAssetRepository, times(1)).save(any(DigitalAsset.class));
        verify(cryptoService, times(1)).encrypt("my-password");
    }

    // Kịch bản 2: Xóa Vault thành công (Mô phỏng FR-27)
    @Test
    void deleteVault_Success() throws Exception {
        UUID vaultId = UUID.randomUUID();
        Vault vault = new Vault();
        vault.setId(vaultId);
        vault.setOwner(mockOwner);
        vault.setStatus(VaultStatus.ACTIVE);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        when(digitalAssetRepository.findByVaultId(vaultId)).thenReturn(Collections.emptyList());
        when(legalDocumentRepository.findByVaultId(vaultId)).thenReturn(Collections.emptyList());

        // Bật synchronization để registerSynchronization trong service không ném IllegalStateException
        TransactionSynchronizationManager.initSynchronization();
        try {
            vaultService.deleteVault(vaultId, ownerId);
            // Kích hoạt afterCommit callbacks thủ công
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // Đảm bảo phải tìm tài sản và tài liệu để xóa trước, rồi mới xóa vault sau
        verify(digitalAssetRepository, times(1)).findByVaultId(vaultId);
        verify(legalDocumentRepository, times(1)).findByVaultId(vaultId);
        verify(vaultRepository, times(1)).delete(vault);
    }

    // Kịch bản 3: Cố tình xóa Vault của người khác -> Phải văng lỗi (Business Rule)
    @Test
    void deleteVault_NotOwner_ThrowsException() {
        UUID vaultId = UUID.randomUUID();
        Vault vault = new Vault();
        vault.setId(vaultId);

        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setEmail("victim@example.com");
        vault.setOwner(otherUser); // Vault thuộc về người khác

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(mockOwner));

        // Bắt lỗi phải là VaultException
        Exception exception = assertThrows(VaultException.class, () -> {
            vaultService.deleteVault(vaultId, ownerId);
        });

        // Nội dung lỗi phải chuẩn xác
        assertEquals("Unauthorized: You don't own this vault", exception.getMessage());
        // Hàm xóa sẽ KHÔNG BAO GIỜ ĐƯỢC CHẠY
        verify(vaultRepository, never()).delete(any());
        // Đảm bảo ghi log với email của kẻ gọi (caller/mockOwner), không phải nạn nhân
        // và phải bao gồm vaultId để nhật ký có đủ context (N2)
        verify(auditLogService).log(
                eq(com.ltld.app.legacyvault.enums.AuditAction.VAULT_DELETE_DENIED),
                eq(AuditResult.FAILURE),
                eq(ownerId),
                eq(mockOwner.getEmail()),
                eq(vaultId),
                eq("Vault"),
                eq(vaultId.toString()),
                eq("Unauthorized: You don't own this vault")
        );
    }

    @Test
    void deleteVault_Success_WithDocumentsAndAssets() throws Exception {
        UUID vaultId = UUID.randomUUID();
        Vault vault = new Vault();
        vault.setId(vaultId);
        vault.setOwner(mockOwner);
        vault.setStatus(VaultStatus.ACTIVE);

        DigitalAsset asset = new DigitalAsset();
        asset.setId(UUID.randomUUID());

        com.ltld.app.legacyvault.entity.LegalDocument doc = new com.ltld.app.legacyvault.entity.LegalDocument();
        doc.setId(UUID.randomUUID());
        doc.setFileUrlEncrypted("uploads/test-file.pdf");

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        when(digitalAssetRepository.findByVaultId(vaultId)).thenReturn(List.of(asset));
        when(legalDocumentRepository.findByVaultId(vaultId)).thenReturn(List.of(doc));

        // Bật synchronization để registerSynchronization trong service không ném IllegalStateException
        TransactionSynchronizationManager.initSynchronization();
        try {
            vaultService.deleteVault(vaultId, ownerId);
            // Kích hoạt afterCommit callbacks thủ công — xác minh file bị xóa SAU KHI DB commit
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(legalDocumentRepository).deleteAllInBatch(List.of(doc));
        verify(digitalAssetRepository).deleteAllInBatch(List.of(asset));
        verify(vaultRepository).delete(vault);
        // File phải bị xóa TRONG afterCommit (sau khi DB commit), không phải trong transaction
        verify(fileStorageService).deleteFile("uploads/test-file.pdf");
        verify(auditLogService).log(
                eq(com.ltld.app.legacyvault.enums.AuditAction.VAULT_DELETED),
                eq(com.ltld.app.legacyvault.enums.AuditResult.SUCCESS),
                eq(ownerId),
                eq(mockOwner.getEmail()),
                eq(vaultId),
                eq("Vault"),
                eq(vaultId.toString()),
                isNull()
        );
    }

    @Test
    void deleteVault_NotActive_ThrowsException() {
        UUID vaultId = UUID.randomUUID();
        Vault vault = new Vault();
        vault.setId(vaultId);
        vault.setOwner(mockOwner);
        vault.setStatus(VaultStatus.ARCHIVED_LOCKED);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));

        Exception exception = assertThrows(VaultException.class, () -> {
            vaultService.deleteVault(vaultId, ownerId);
        });

        assertEquals("Cannot delete vault that is not in active state", exception.getMessage());
        verify(vaultRepository, never()).delete(any());
    }

    // ===== FR-02: Chỉ định người thụ hưởng =====

    private Vault activeVault() {
        mockOwner.setEmail("owner@example.com");
        mockOwner.setFullName("Owner");
        Vault vault = new Vault();
        vault.setId(UUID.randomUUID());
        vault.setName("My Vault");
        vault.setOwner(mockOwner);
        vault.setStatus(VaultStatus.ACTIVE);
        return vault;
    }

    private User userWith(String email, UserStatus status) {
        return User.builder().id(UUID.randomUUID()).email(email).fullName("Ben").status(status).build();
    }

    private AssignBeneficiaryRequest assignRequest(String email, Boolean confirmReplace) {
        AssignBeneficiaryRequest request = new AssignBeneficiaryRequest();
        request.setEmail(email);
        request.setConfirmReplace(confirmReplace);
        return request;
    }

    private void assertVaultError(HttpStatus expected, Runnable call) {
        VaultException ex = assertThrows(VaultException.class, call::run);
        assertEquals(expected, ex.getStatus());
    }

    @Test
    void assignBeneficiary_verifiedUser_linksImmediately() {
        Vault vault = activeVault();
        User ben = userWith("ben@example.com", UserStatus.ACTIVE);
        when(vaultRepository.findByIdForUpdate(vault.getId())).thenReturn(Optional.of(vault));
        when(userRepository.findByEmail("ben@example.com")).thenReturn(Optional.of(ben));

        AssignBeneficiaryResponse response =
                vaultService.assignBeneficiary(ownerId, vault.getId(), assignRequest("  Ben@Example.com ", null));

        assertSame(ben, vault.getBeneficiary());
        assertNull(vault.getBeneficiaryEmail());
        assertFalse(response.isPendingRegistration());
        assertFalse(response.isReplaced());
        assertEquals("Ben", response.getBeneficiaryName());
        verify(vaultRepository).save(vault);
        verify(emailSender).sendBeneficiaryAssignedNotice("ben@example.com", "Owner", "My Vault");
        verify(emailSender).sendBeneficiaryAssignmentConfirmation("owner@example.com", "My Vault", "ben@example.com", false);
        verify(emailSender, never()).sendBeneficiaryInvitation(any(), any(), any());
        // m5: detail chỉ có user id, không có email người thụ hưởng
        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_ASSIGNED), eq(AuditResult.SUCCESS), eq(ownerId),
                any(), eq(vault.getId()), eq("Vault"), eq(vault.getId().toString()),
                eq("beneficiaryUserId=" + ben.getId()));
    }

    @Test
    void assignBeneficiary_noAccount_storesPendingEmailAndInvites() {
        Vault vault = activeVault();
        when(vaultRepository.findByIdForUpdate(vault.getId())).thenReturn(Optional.of(vault));
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        AssignBeneficiaryResponse response =
                vaultService.assignBeneficiary(ownerId, vault.getId(), assignRequest("new@example.com", null));

        assertNull(vault.getBeneficiary());
        assertEquals("new@example.com", vault.getBeneficiaryEmail());
        assertTrue(response.isPendingRegistration());
        verify(emailSender).sendBeneficiaryInvitation("new@example.com", "Owner", "My Vault");
        verify(emailSender).sendBeneficiaryAssignmentConfirmation("owner@example.com", "My Vault", "new@example.com", true);
        // m5: người chưa có tài khoản chỉ ghi cờ chờ đăng ký, không ghi email
        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_ASSIGNED), eq(AuditResult.SUCCESS), eq(ownerId),
                any(), eq(vault.getId()), eq("Vault"), eq(vault.getId().toString()), eq("pendingRegistration=true"));
    }

    @Test
    void assignBeneficiary_unverifiedAccount_treatedAsPending() {
        Vault vault = activeVault();
        when(vaultRepository.findByIdForUpdate(vault.getId())).thenReturn(Optional.of(vault));
        when(userRepository.findByEmail("pending@example.com"))
                .thenReturn(Optional.of(userWith("pending@example.com", UserStatus.PENDING)));

        vaultService.assignBeneficiary(ownerId, vault.getId(), assignRequest("pending@example.com", null));

        assertNull(vault.getBeneficiary());
        assertEquals("pending@example.com", vault.getBeneficiaryEmail());
        verify(emailSender).sendBeneficiaryInvitation("pending@example.com", "Owner", "My Vault");
    }

    @Test
    void assignBeneficiary_vaultNotFound_returns404() {
        UUID vaultId = UUID.randomUUID();
        when(vaultRepository.findByIdForUpdate(vaultId)).thenReturn(Optional.empty());

        assertVaultError(HttpStatus.NOT_FOUND,
                () -> vaultService.assignBeneficiary(ownerId, vaultId, assignRequest("ben@example.com", null)));
    }

    @Test
    void assignBeneficiary_notOwner_returns403AndAudits() {
        Vault vault = activeVault();
        when(vaultRepository.findByIdForUpdate(vault.getId())).thenReturn(Optional.of(vault));
        UUID otherUserId = UUID.randomUUID();

        assertVaultError(HttpStatus.FORBIDDEN,
                () -> vaultService.assignBeneficiary(otherUserId, vault.getId(), assignRequest("ben@example.com", null)));

        verify(vaultRepository, never()).save(any());
        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_ASSIGN_DENIED), eq(AuditResult.FAILURE),
                eq(otherUserId), any(), eq(vault.getId()), eq("Vault"), eq(vault.getId().toString()), eq("not owner"));
    }

    @Test
    void assignBeneficiary_vaultNotActive_returns409() {
        Vault vault = activeVault();
        vault.setStatus(VaultStatus.UNLOCKED);
        when(vaultRepository.findByIdForUpdate(vault.getId())).thenReturn(Optional.of(vault));

        assertVaultError(HttpStatus.CONFLICT,
                () -> vaultService.assignBeneficiary(ownerId, vault.getId(), assignRequest("ben@example.com", null)));

        verify(vaultRepository, never()).save(any());
        // T7: audit từ chối kèm trạng thái Vault
        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_ASSIGN_DENIED), eq(AuditResult.FAILURE), eq(ownerId),
                eq("owner@example.com"), eq(vault.getId()), eq("Vault"), eq(vault.getId().toString()),
                eq("vault status UNLOCKED"));
        verifyNoInteractions(emailSender);
    }

    @Test
    void assignBeneficiary_self_returns400() {
        Vault vault = activeVault();
        when(vaultRepository.findByIdForUpdate(vault.getId())).thenReturn(Optional.of(vault));

        assertVaultError(HttpStatus.BAD_REQUEST,
                () -> vaultService.assignBeneficiary(ownerId, vault.getId(), assignRequest("OWNER@example.com", null)));

        verify(vaultRepository, never()).save(any());
        // T8: không gửi email, không ghi audit nào (kể cả SUCCESS)
        verifyNoInteractions(emailSender, auditLogService);
    }

    @Test
    void assignBeneficiary_emailIsOwnersExecutor_returns409() {
        Vault vault = activeVault();
        when(vaultRepository.findByIdForUpdate(vault.getId())).thenReturn(Optional.of(vault));
        when(executorAssignmentRepository.existsLiveExecutorWithEmail(ownerId, "exec@example.com")).thenReturn(true);

        VaultException ex = assertThrows(VaultException.class,
                () -> vaultService.assignBeneficiary(ownerId, vault.getId(), assignRequest(" Exec@Example.com ", null)));

        // Q26: Executor của Owner không được đồng thời là người thụ hưởng
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Người này đang là Digital Executor của bạn, không thể đồng thời là người thụ hưởng.",
                ex.getMessage());
        assertNull(vault.getBeneficiary());
        assertNull(vault.getBeneficiaryEmail());
        verify(vaultRepository, never()).save(any());
        verifyNoInteractions(emailSender, auditLogService);
    }

    @Test
    void assignBeneficiary_currentBeneficiaryIsNowExecutor_returns409() {
        // Q26 đứng TRƯỚC nhánh no-op "gán lại đúng người hiện tại": không được trả về im lặng
        Vault vault = activeVault();
        User ben = userWith("ben@example.com", UserStatus.ACTIVE);
        vault.setBeneficiary(ben);
        when(vaultRepository.findByIdForUpdate(vault.getId())).thenReturn(Optional.of(vault));
        when(executorAssignmentRepository.existsLiveExecutorWithEmail(ownerId, "ben@example.com")).thenReturn(true);

        assertVaultError(HttpStatus.CONFLICT,
                () -> vaultService.assignBeneficiary(ownerId, vault.getId(), assignRequest("ben@example.com", null)));

        assertSame(ben, vault.getBeneficiary());
        verify(vaultRepository, never()).save(any());
        verifyNoInteractions(emailSender, auditLogService);
    }

    @Test
    void assignBeneficiary_existingBeneficiaryWithoutConfirm_returns409() {
        Vault vault = activeVault();
        vault.setBeneficiary(userWith("old@example.com", UserStatus.ACTIVE));
        when(vaultRepository.findByIdForUpdate(vault.getId())).thenReturn(Optional.of(vault));

        assertVaultError(HttpStatus.CONFLICT,
                () -> vaultService.assignBeneficiary(ownerId, vault.getId(), assignRequest("ben@example.com", false)));

        assertEquals("old@example.com", vault.getBeneficiary().getEmail());
        verify(vaultRepository, never()).save(any());
        verifyNoInteractions(emailSender);
    }

    @Test
    void assignBeneficiary_pendingInviteWithoutConfirm_returns409() {
        Vault vault = activeVault();
        vault.setBeneficiaryEmail("invited@example.com");
        when(vaultRepository.findByIdForUpdate(vault.getId())).thenReturn(Optional.of(vault));

        assertVaultError(HttpStatus.CONFLICT,
                () -> vaultService.assignBeneficiary(ownerId, vault.getId(), assignRequest("ben@example.com", null)));
    }

    @Test
    void assignBeneficiary_existingBeneficiaryWithConfirm_replaces() {
        Vault vault = activeVault();
        User old = userWith("old@example.com", UserStatus.ACTIVE);
        vault.setBeneficiary(old);
        when(vaultRepository.findByIdForUpdate(vault.getId())).thenReturn(Optional.of(vault));
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        AssignBeneficiaryResponse response =
                vaultService.assignBeneficiary(ownerId, vault.getId(), assignRequest("new@example.com", true));

        // Người cũ bị gỡ, email mới ở trạng thái chờ đăng ký
        assertNull(vault.getBeneficiary());
        assertEquals("new@example.com", vault.getBeneficiaryEmail());
        assertTrue(response.isReplaced());
        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_REPLACED), eq(AuditResult.SUCCESS), eq(ownerId),
                any(), eq(vault.getId()), eq("Vault"), eq(vault.getId().toString()),
                eq("pendingRegistration=true, previousUserId=" + old.getId()));
    }

    // ===== m2 (T5): email chỉ gửi sau khi transaction commit =====

    private void runAfterCommitCallbacks() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    @Test
    void assignBeneficiary_verifiedUser_sendsEmailsOnlyAfterCommit() {
        Vault vault = activeVault();
        when(vaultRepository.findByIdForUpdate(vault.getId())).thenReturn(Optional.of(vault));
        when(userRepository.findByEmail("ben@example.com"))
                .thenReturn(Optional.of(userWith("ben@example.com", UserStatus.ACTIVE)));

        TransactionSynchronizationManager.initSynchronization();
        try {
            vaultService.assignBeneficiary(ownerId, vault.getId(), assignRequest("ben@example.com", null));
            verifyNoInteractions(emailSender);

            runAfterCommitCallbacks();

            verify(emailSender).sendBeneficiaryAssignedNotice("ben@example.com", "Owner", "My Vault");
            verify(emailSender).sendBeneficiaryAssignmentConfirmation("owner@example.com", "My Vault", "ben@example.com", false);
            verifyNoMoreInteractions(emailSender);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void assignBeneficiary_pendingRegistration_sendsInvitationOnlyAfterCommit() {
        Vault vault = activeVault();
        when(vaultRepository.findByIdForUpdate(vault.getId())).thenReturn(Optional.of(vault));
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        TransactionSynchronizationManager.initSynchronization();
        try {
            vaultService.assignBeneficiary(ownerId, vault.getId(), assignRequest("new@example.com", null));
            verifyNoInteractions(emailSender);

            runAfterCommitCallbacks();

            verify(emailSender).sendBeneficiaryInvitation("new@example.com", "Owner", "My Vault");
            verify(emailSender).sendBeneficiaryAssignmentConfirmation("owner@example.com", "My Vault", "new@example.com", true);
            verifyNoMoreInteractions(emailSender);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void assignBeneficiary_sameBeneficiary_isNoOp() {
        Vault vault = activeVault();
        User ben = userWith("ben@example.com", UserStatus.ACTIVE);
        vault.setBeneficiary(ben);
        when(vaultRepository.findByIdForUpdate(vault.getId())).thenReturn(Optional.of(vault));

        AssignBeneficiaryResponse response =
                vaultService.assignBeneficiary(ownerId, vault.getId(), assignRequest("ben@example.com", null));

        assertFalse(response.isReplaced());
        assertSame(ben, vault.getBeneficiary());
        verify(vaultRepository, never()).save(any());
        verifyNoInteractions(emailSender);
    }

    // ===== FR-01/FR-27/FR-20: Vault CRUD & Admin Detail Tests =====

    @Test
    @DisplayName("getMyVaults: Lấy danh sách két của Owner thành công")
    void getMyVaults_Success() {
        Vault vault = activeVault();
        when(vaultRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId))
                .thenReturn(List.of(vault));

        List<VaultResponse> result = vaultService.getMyVaults(ownerId);

        assertEquals(1, result.size());
        assertEquals("My Vault", result.get(0).getName());
        assertEquals(ownerId, result.get(0).getOwnerId());
        verify(vaultRepository).findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    @Test
    @DisplayName("getMyVaults: Trả về danh sách rỗng khi Owner chưa có két")
    void getMyVaults_EmptyList() {
        when(vaultRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId))
                .thenReturn(List.of());

        List<VaultResponse> result = vaultService.getMyVaults(ownerId);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getVaultDetail: Owner xem chi tiết két của mình thành công")
    void getVaultDetail_AsOwner_Success() {
        Vault vault = activeVault();
        UUID vId = vault.getId();
        when(vaultRepository.findById(vId)).thenReturn(Optional.of(vault));
        when(legalDocumentRepository.countByVaultId(vId)).thenReturn(2L);
        when(digitalAssetRepository.countByVaultIdAndStatus(vId, AssetStatus.ACTIVE)).thenReturn(3L);

        VaultDetailResponse detail = vaultService.getVaultDetail(vId, ownerId);

        assertEquals(vId, detail.getId());
        assertEquals("owner@example.com", detail.getOwnerEmail());
        assertEquals(2L, detail.getTotalDocuments());
        assertEquals(3L, detail.getTotalAssets());
    }

    @Test
    @DisplayName("getAdminVaultDetail: Admin xem chi tiết bất kỳ két nào thành công và có ghi audit log")
    void getAdminVaultDetail_AsAdmin_Success() {
        Vault vault = activeVault();
        UUID vId = vault.getId();
        when(vaultRepository.findById(vId)).thenReturn(Optional.of(vault));
        when(legalDocumentRepository.countByVaultId(vId)).thenReturn(0L);
        when(digitalAssetRepository.countByVaultIdAndStatus(vId, AssetStatus.ACTIVE)).thenReturn(0L);

        UUID adminId = UUID.randomUUID();
        User adminUser = userWith("admin@example.com", UserStatus.ACTIVE);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));

        AdminVaultDetailResponse detail = vaultService.getAdminVaultDetail(vId, adminId);

        assertEquals(vId, detail.getId());
        assertEquals("owner@example.com", detail.getOwnerEmail());
        verify(auditLogService).log(eq(AuditAction.ADMIN_VAULT_VIEWED), eq(AuditResult.SUCCESS),
                eq(adminId), eq("admin@example.com"), eq(vId),
                eq("Vault"), eq(vId.toString()), any());
    }

    @Test
    @DisplayName("getVaultDetail: Người khác xem lén két -> Bị chặn 404 Not Found (m1)")
    void getVaultDetail_Unauthorized_ThrowsNotFound() {
        Vault vault = activeVault();
        UUID attackerId = UUID.randomUUID();
        when(vaultRepository.findById(vault.getId())).thenReturn(Optional.of(vault));

        VaultException ex = assertThrows(VaultException.class,
                () -> vaultService.getVaultDetail(vault.getId(), attackerId));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("getVaultDetail: Két không tồn tại -> Báo 404 Not Found")
    void getVaultDetail_NotFound_ThrowsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(vaultRepository.findById(unknownId)).thenReturn(Optional.empty());

        VaultException ex = assertThrows(VaultException.class,
                () -> vaultService.getVaultDetail(unknownId, ownerId));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("updateVault: Cập nhật tên và mô tả khi két ACTIVE thành công")
    void updateVault_Success() {
        Vault vault = activeVault();
        UUID vId = vault.getId();
        when(vaultRepository.findById(vId)).thenReturn(Optional.of(vault));
        when(vaultRepository.save(any(Vault.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateVaultRequest request = new UpdateVaultRequest("Tên két mới", "Mô tả mới");
        VaultResponse response = vaultService.updateVault(vId, ownerId, request);

        assertEquals("Tên két mới", response.getName());
        assertEquals("Mô tả mới", response.getDescription());
        verify(vaultModificationGuard).ensureModifiable(vault);
        verify(vaultRepository).save(vault);
        verify(auditLogService).log(eq(AuditAction.VAULT_UPDATED), eq(AuditResult.SUCCESS),
                eq(ownerId), eq("owner@example.com"), eq(vId),
                eq("Vault"), eq(vId.toString()), any());
    }

    @Test
    @DisplayName("updateVault: Người khác sửa két -> Chặn 404 Not Found (m1)")
    void updateVault_Unauthorized_ThrowsNotFound() {
        Vault vault = activeVault();
        UUID attackerId = UUID.randomUUID();
        when(vaultRepository.findById(vault.getId())).thenReturn(Optional.of(vault));
        UpdateVaultRequest request = new UpdateVaultRequest("Tên két mới", "Mô tả mới");

        VaultException ex = assertThrows(VaultException.class,
                () -> vaultService.updateVault(vault.getId(), attackerId, request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(vaultRepository, never()).save(any());
        verifyNoInteractions(vaultModificationGuard);
    }

    @Test
    @DisplayName("updateVault (m3): Két bị chặn bởi guard (DMS triggered / có xác minh mở) -> Chặn 409 CONFLICT")
    void updateVault_guardBlocked_throwsConflict() {
        Vault vault = activeVault();
        when(vaultRepository.findById(vault.getId())).thenReturn(Optional.of(vault));
        doThrow(new VaultException("Cannot modify vault while a verification request is pending or approved", HttpStatus.CONFLICT))
                .when(vaultModificationGuard).ensureModifiable(vault);

        UpdateVaultRequest request = new UpdateVaultRequest("Tên két mới", "Mô tả mới");

        VaultException ex = assertThrows(VaultException.class,
                () -> vaultService.updateVault(vault.getId(), ownerId, request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(vaultRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateVault: Két không ACTIVE -> Guard ném 400 Bad Request")
    void updateVault_NotActive_ThrowsBadRequest() {
        Vault vault = activeVault();
        vault.setStatus(VaultStatus.UNLOCKED);
        when(vaultRepository.findById(vault.getId())).thenReturn(Optional.of(vault));
        doThrow(new VaultException("Cannot modify vault that is not in active state", HttpStatus.BAD_REQUEST))
                .when(vaultModificationGuard).ensureModifiable(vault);

        UpdateVaultRequest request = new UpdateVaultRequest("Tên két mới", "Mô tả mới");

        VaultException ex = assertThrows(VaultException.class,
                () -> vaultService.updateVault(vault.getId(), ownerId, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(vaultRepository, never()).save(any());
    }

    @Test
    @DisplayName("getAllVaultsForAdmin: Admin lọc theo trạng thái và phân trang thành công")
    void getAllVaultsForAdmin_Success() {
        Vault vault = activeVault();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Vault> page = new PageImpl<>(List.of(vault), pageable, 1);
        when(vaultRepository.findByStatus(VaultStatus.ACTIVE, pageable)).thenReturn(page);

        Page<VaultResponse> result = vaultService.getAllVaultsForAdmin(VaultStatus.ACTIVE, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("My Vault", result.getContent().get(0).getName());
    }
}
