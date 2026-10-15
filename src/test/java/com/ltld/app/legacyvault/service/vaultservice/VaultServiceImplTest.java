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
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
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

        vaultService.deleteVault(vaultId, ownerId);

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

        vaultService.deleteVault(vaultId, ownerId);

        verify(legalDocumentRepository).deleteAllInBatch(List.of(doc));
        verify(digitalAssetRepository).deleteAllInBatch(List.of(asset));
        verify(vaultRepository).delete(vault);
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
}
