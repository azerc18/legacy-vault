package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.dto.vaultdto.AdminVaultDetailResponse;
import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryRequest;
import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryResponse;
import com.ltld.app.legacyvault.dto.vaultdto.CreateVaultRequest;
import com.ltld.app.legacyvault.dto.vaultdto.UpdateVaultRequest;
import com.ltld.app.legacyvault.dto.vaultdto.VaultDetailResponse;
import com.ltld.app.legacyvault.dto.vaultdto.VaultResponse;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoServiceImpl;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VaultServiceImpl implements VaultService {

    private final VaultRepository vaultRepository;
    private final DigitalAssetRepository digitalAssetRepository;
    private final LegalDocumentRepository legalDocumentRepository;
    private final UserRepository userRepository;
    private final CryptoService cryptoService;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final EmailSender emailSender;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final VaultModificationGuard vaultModificationGuard;
    private final DmsConfigService dmsConfigService;

    @Override
    @Transactional // Nếu có lỗi xảy ra ở bất kỳ dòng nào, DB sẽ undo lại toàn bộ (rollback)
    public Vault createVault(UUID ownerId, CreateVaultRequest request) throws Exception {
        // 1. Tìm thông tin chủ sở hữu (Owner)
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new VaultException("Owner not found", HttpStatus.NOT_FOUND));

        // 2. Tạo két sắt (Vault)
        Vault vault = Vault.builder()
                .owner(owner)
                .name(request.getName())
                .description(request.getDescription())
                .status(VaultStatus.ACTIVE)
                .build();

        vault = vaultRepository.save(vault);

        // 3. Nếu người dùng có gửi kèm danh sách tài sản, ta sẽ MÃ HÓA rồi lưu
        if (request.getAssets() != null) {
            for (CreateVaultRequest.DigitalAssetDto assetDto : request.getAssets()) {
                DigitalAsset asset = DigitalAsset.builder()
                        .vault(vault)
                        .assetType(assetDto.getAssetType())
                        .assetName(assetDto.getAssetName())
                        // ĐÂY LÀ CHỖ QUAN TRỌNG NHẤT: Gọi CryptoService để mã hóa chữ gốc thành loằng ngoằng
                        .encryptedSecret(cryptoService.encrypt(assetDto.getSecret()))
                        .encryptionKeyRef(CryptoServiceImpl.CURRENT_KEY_REF)
                        .notesEncrypted(assetDto.getNotes() != null ? cryptoService.encrypt(assetDto.getNotes()) : null)
                        .status(AssetStatus.ACTIVE)
                        .build();
                digitalAssetRepository.save(asset);
            }
        }

        auditLogService.log(AuditAction.VAULT_CREATED, AuditResult.SUCCESS, 
                ownerId, owner.getEmail(), vault.getId(),
                "Vault", vault.getId().toString(), null);
        return vault;
    }

    @Override
    @Transactional
    public void deleteVault(UUID vaultId, UUID ownerId) throws Exception {
        // 1. Tìm Vault — dùng vault.getOwner() để lấy email, tránh query UserRepository thừa (#16)
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new VaultException("Vault not found", HttpStatus.NOT_FOUND));

        // 2. Chỉ chủ sở hữu mới được xóa
        if (!vault.getOwner().getId().equals(ownerId)) {
            String callerEmail = userRepository.findById(ownerId)
                    .map(User::getEmail).orElse("unknown");
            auditLogService.log(AuditAction.VAULT_DELETE_DENIED, AuditResult.FAILURE,
                    ownerId, callerEmail, vaultId, "Vault", vaultId.toString(),
                    "Unauthorized: You don't own this vault");
            throw new VaultException("Unauthorized: You don't own this vault", HttpStatus.FORBIDDEN);
        }

        String ownerEmail = vault.getOwner().getEmail();

        // 3. Nghiệp vụ: Chặn xóa nếu quy trình bàn giao (DMS) đã kích hoạt (FR-01)
        try {
            dmsConfigService.assertHandoverNotStarted(ownerId);
        } catch (DmsException ex) {
            auditLogService.log(AuditAction.VAULT_DELETE_DENIED, AuditResult.FAILURE,
                    ownerId, ownerEmail, vaultId, "Vault", vaultId.toString(),
                    "The handover process has already started");
            throw ex;
        }

        // Trạng thái không phải ACTIVE thì từ chối xóa — ghi audit kèm vaultId (FR-01)
        if (vault.getStatus() != VaultStatus.ACTIVE) {
            auditLogService.log(AuditAction.VAULT_DELETE_DENIED, AuditResult.FAILURE,
                    ownerId, ownerEmail, vaultId, "Vault", vaultId.toString(),
                    "The vault is not active");
            throw new VaultException("Cannot delete vault that is not in active state");
        }

        // 4. Thu thập đường dẫn file TRƯỚC khi xóa DB — N1 fix
        List<LegalDocument> docs = legalDocumentRepository.findByVaultId(vaultId);
        List<String> filesToDelete = docs.stream().map(LegalDocument::getFileUrlEncrypted).toList();

        // Xóa DB bằng batch (nhanh hơn forEach) — ràng buộc FK theo thứ tự
        legalDocumentRepository.deleteAllInBatch(docs);
        digitalAssetRepository.deleteAllInBatch(digitalAssetRepository.findByVaultId(vaultId));

        // 5. Đập cái két (Vault)
        vaultRepository.delete(vault);

        // N1: Chỉ xóa file vật lý SAU KHI DB commit thành công
        // Nếu bước trên rollback → file vẫn còn nguyên, không mất dữ liệu pháp lý
        // Method có @Transactional nên synchronization luôn active trong production
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                filesToDelete.forEach(url -> {
                    try {
                        fileStorageService.deleteFile(url);
                    } catch (RuntimeException e) {
                        // DB đã commit: để lại file mồ côi (vô hại), không làm hỏng request
                        log.warn("FR-01/deleteVault: file not removed after vault deleted: {}", e.getMessage());
                    }
                });
            }
        });

        auditLogService.log(AuditAction.VAULT_DELETED, AuditResult.SUCCESS,
                ownerId, ownerEmail, vault.getId(),
                "Vault", vaultId.toString(), null);
    }

    // FR-02: Owner chỉ định (hoặc thay thế) người thụ hưởng cho Vault
    // READ_COMMITTED: MySQL mặc định REPEATABLE READ nên câu đọc thường có thể thấy snapshot cũ. Sau khi khóa DmsConfig,
    // existsLiveExecutorWithEmail phải thấy Executor mà FR-06 vừa commit (FR-06 cũng khóa DmsConfig trước khi kiểm),
    // để Q26 không bị write-skew giữa FR-02 và FR-06 (giống ExecutorManagementServiceImpl).
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AssignBeneficiaryResponse assignBeneficiary(UUID ownerId, UUID vaultId, AssignBeneficiaryRequest request) {
        // Khóa DmsConfig TRƯỚC Vault (DmsConfig -> Vault -> User), cùng thứ tự với FR-06/FR-07 để không deadlock;
        // DMS đã kích hoạt bàn giao thì không được đổi người thụ hưởng -> DmsException 409.
        dmsConfigService.assertHandoverNotStarted(ownerId);

        // Khóa dòng Vault để 2 request gán đồng thời không cùng vượt qua bước xác nhận ghi đè
        Vault vault = vaultRepository.findByIdForUpdate(vaultId)
                .orElseThrow(() -> new VaultException("Vault not found", HttpStatus.NOT_FOUND));

        User owner = vault.getOwner();
        if (!owner.getId().equals(ownerId)) {
            auditLogService.log(AuditAction.BENEFICIARY_ASSIGN_DENIED, AuditResult.FAILURE, ownerId, null, vaultId,
                    "Vault", vaultId.toString(), "not owner");
            throw new VaultException("You do not have permission to act on this vault", HttpStatus.FORBIDDEN);
        }

        // Vault đã bước vào quy trình bàn giao thì không được đổi người thụ hưởng (tương tự BR-08)
        if (vault.getStatus() != VaultStatus.ACTIVE) {
            auditLogService.log(AuditAction.BENEFICIARY_ASSIGN_DENIED, AuditResult.FAILURE, ownerId, owner.getEmail(), vaultId,
                    "Vault", vaultId.toString(), "vault status " + vault.getStatus());
            throw new VaultException("A beneficiary can only be assigned while the vault is ACTIVE",
                    HttpStatus.CONFLICT);
        }

        String email = request.getEmail().trim().toLowerCase();
        if (email.equals(owner.getEmail())) {
            throw new VaultException("You cannot assign yourself as the beneficiary", HttpStatus.BAD_REQUEST);
        }

        // Q26: Executor (chưa bị thu hồi) của Owner không được đồng thời là người thụ hưởng.
        // Đặt trước bước "gán lại đúng người" để cả trường hợp người thụ hưởng hiện tại vừa trở thành Executor cũng bị chặn.
        if (executorAssignmentRepository.existsLiveExecutorWithEmail(ownerId, email)) {
            throw new VaultException("This person is your Digital Executor and cannot also be your beneficiary.",
                    HttpStatus.CONFLICT);
        }

        String currentEmail = vault.getBeneficiary() != null
                ? vault.getBeneficiary().getEmail()
                : vault.getBeneficiaryEmail();

        // Gán lại đúng người hiện tại: không thay đổi gì, không gửi email
        if (email.equals(currentEmail)) {
            return toAssignResponse(vault, email, false);
        }

        // FR-02 (2a): đã có người thụ hưởng thì phải xác nhận ghi đè
        boolean replacing = currentEmail != null;
        if (replacing && !Boolean.TRUE.equals(request.getConfirmReplace())) {
            throw new VaultException("The vault already has a beneficiary (" + currentEmail
                    + "). Resend with confirmReplace=true to replace them.", HttpStatus.CONFLICT);
        }

        // m5: lấy định danh người cũ trước khi ghi đè, để audit không cần ghi email
        User previousBeneficiary = vault.getBeneficiary();

        // Chỉ liên kết ngay với tài khoản đã xác minh email; còn lại lưu email chờ và liên kết khi xác minh xong
        User beneficiary = userRepository.findByEmail(email)
                .filter(u -> u.getStatus() != UserStatus.PENDING)
                .orElse(null);
        vault.setBeneficiary(beneficiary);
        vault.setBeneficiaryEmail(beneficiary == null ? email : null);
        vaultRepository.save(vault);

        boolean pending = beneficiary == null;
        // m5: audit nằm trong nhật ký Vault (FR-05) nên không ghi email bên thứ ba; chỉ ghi user id hoặc cờ chờ đăng ký
        String detail = (pending ? "pendingRegistration=true" : "beneficiaryUserId=" + beneficiary.getId());
        if (replacing) {
            detail += previousBeneficiary != null
                    ? ", previousUserId=" + previousBeneficiary.getId()
                    : ", previousPendingRegistration=true";
        }
        auditLogService.log(replacing ? AuditAction.BENEFICIARY_REPLACED : AuditAction.BENEFICIARY_ASSIGNED,
                AuditResult.SUCCESS, ownerId, owner.getEmail(), vaultId, "Vault", vaultId.toString(), detail);

        // m2: gửi email sau khi commit; lấy sẵn giá trị để không chạm lazy entity sau commit
        String ownerName = owner.getFullName();
        String ownerEmail = owner.getEmail();
        String vaultName = vault.getName();
        runAfterCommit(() -> {
            if (pending) {
                emailSender.sendBeneficiaryInvitation(email, ownerName, vaultName);
            } else {
                emailSender.sendBeneficiaryAssignedNotice(email, ownerName, vaultName);
            }
            emailSender.sendBeneficiaryAssignmentConfirmation(ownerEmail, vaultName, email, pending);
        });

        return toAssignResponse(vault, email, replacing);
    }

    // Chạy sau khi transaction commit; không có transaction (unit test) thì chạy ngay
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private AssignBeneficiaryResponse toAssignResponse(Vault vault, String email, boolean replaced) {
        User beneficiary = vault.getBeneficiary();
        return AssignBeneficiaryResponse.builder()
                .vaultId(vault.getId())
                .beneficiaryEmail(email)
                .beneficiaryName(beneficiary != null ? beneficiary.getFullName() : null)
                .pendingRegistration(beneficiary == null)
                .replaced(replaced)
                .build();
    }

    // FR-27: Owner xem danh sách Vault của mình
    @Override
    @Transactional(readOnly = true)
    public List<VaultResponse> getMyVaults(UUID ownerId) {
        return vaultRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)
                .stream()
                .map(vault -> VaultResponse.builder()
                        .id(vault.getId())
                        .name(vault.getName())
                        .description(vault.getDescription())
                        .status(vault.getStatus())
                        .ownerId(vault.getOwner() != null ? vault.getOwner().getId() : ownerId)
                        .createdAt(vault.getCreatedAt())
                        .updatedAt(vault.getUpdatedAt())
                        .build())
                .toList();
    }

    // FR-27: Owner xem chi tiết một Vault (trả 404 nếu không phải chủ sở hữu)
    @Override
    @Transactional(readOnly = true)
    public VaultDetailResponse getVaultDetail(UUID vaultId, UUID ownerId) {
        // n3: Tái sử dụng loadOwnedVault để kiểm tra sở hữu và chống lộ ID (404)
        Vault vault = loadOwnedVault(vaultId, ownerId);

        long docCount = legalDocumentRepository.countByVaultId(vaultId);
        long assetCount = digitalAssetRepository.countByVaultIdAndStatus(vaultId, AssetStatus.ACTIVE);

        return VaultDetailResponse.builder()
                .id(vault.getId())
                .name(vault.getName())
                .description(vault.getDescription())
                .status(vault.getStatus())
                .ownerId(vault.getOwner().getId())
                .ownerEmail(vault.getOwner().getEmail())
                .beneficiaryId(vault.getBeneficiary() != null ? vault.getBeneficiary().getId() : null)
                .beneficiaryEmail(vault.getBeneficiary() != null ? vault.getBeneficiary().getEmail() : vault.getBeneficiaryEmail())
                .unlockedAt(vault.getUnlockedAt())
                .claimDeadlineAt(vault.getClaimDeadlineAt())
                .createdAt(vault.getCreatedAt())
                .updatedAt(vault.getUpdatedAt())
                .totalDocuments(docCount)
                .totalAssets(assetCount)
                .build();
    }

    // FR-27: Owner cập nhật tên/mô tả Vault
    @Override
    @Transactional
    public VaultResponse updateVault(UUID vaultId, UUID ownerId, UpdateVaultRequest request) {
        // m1: Trả 404 thay vì 403 để tránh lộ sự tồn tại của vault khi non-owner tra cứu
        Vault vault = loadOwnedVault(vaultId, ownerId);

        // m3: Áp dụng quy tắc chặn sửa đổi Vault dùng chung (ACTIVE, DMS, hồ sơ xác minh mở)
        vaultModificationGuard.ensureModifiable(vault);

        vault.setName(request.getName());
        vault.setDescription(request.getDescription());
        Vault updated = vaultRepository.save(vault);

        // m4: Ghi nhật ký cập nhật để audit trail đầy đủ
        auditLogService.log(AuditAction.VAULT_UPDATED, AuditResult.SUCCESS,
                ownerId, vault.getOwner().getEmail(), vaultId,
                "Vault", vaultId.toString(), null);

        return VaultResponse.builder()
                .id(updated.getId())
                .name(updated.getName())
                .description(updated.getDescription())
                .status(updated.getStatus())
                .ownerId(updated.getOwner() != null ? updated.getOwner().getId() : ownerId)
                .createdAt(updated.getCreatedAt())
                .updatedAt(updated.getUpdatedAt())
                .build();
    }

    // FR-20/Admin: Admin xem toàn bộ Vault, lọc theo trạng thái, phân trang
    @Override
    @Transactional(readOnly = true)
    public Page<VaultResponse> getAllVaultsForAdmin(VaultStatus status, Pageable pageable) {
        Page<Vault> page = (status != null)
                ? vaultRepository.findByStatus(status, pageable)
                : vaultRepository.findAll(pageable);

        return page.map(v -> VaultResponse.builder()
                .id(v.getId())
                .name(v.getName())
                .description(v.getDescription())
                .status(v.getStatus())
                .ownerId(v.getOwner().getId())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build());
    }

    // FR-20/Admin: Xem chi tiết một Vault bất kỳ
    @Override
    @Transactional(readOnly = true)
    public AdminVaultDetailResponse getAdminVaultDetail(UUID vaultId, UUID adminId) {
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new VaultException("Vault not found", HttpStatus.NOT_FOUND));

        long docCount = legalDocumentRepository.countByVaultId(vaultId);
        long assetCount = digitalAssetRepository.countByVaultIdAndStatus(vaultId, AssetStatus.ACTIVE);

        // m2: Ghi audit log khi Admin xem chi tiết Vault
        String adminEmail = userRepository.findById(adminId)
                .map(User::getEmail).orElse("unknown-admin");
        auditLogService.log(AuditAction.ADMIN_VAULT_VIEWED, AuditResult.SUCCESS,
                adminId, adminEmail, vaultId,
                "Vault", vaultId.toString(), "Admin viewed vault detail");

        return AdminVaultDetailResponse.builder()
                .id(vault.getId())
                .name(vault.getName())
                .description(vault.getDescription())
                .status(vault.getStatus())
                .ownerId(vault.getOwner().getId())
                .ownerEmail(vault.getOwner().getEmail())
                .unlockedAt(vault.getUnlockedAt())
                .claimDeadlineAt(vault.getClaimDeadlineAt())
                .createdAt(vault.getCreatedAt())
                .updatedAt(vault.getUpdatedAt())
                .totalDocuments(docCount)
                .totalAssets(assetCount)
                .build();
    }

    // Tải Vault và kiểm tra quyền sở hữu — trả 404 (không phải 403) để tránh lộ sự tồn tại của vault (m1, n3)
    private Vault loadOwnedVault(UUID vaultId, UUID ownerId) {
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new VaultException("Vault not found", HttpStatus.NOT_FOUND));
        if (!vault.getOwner().getId().equals(ownerId)) {
            // Không phân biệt "vault không tồn tại" và "không phải chủ" để chống ID enumeration
            throw new VaultException("Vault not found", HttpStatus.NOT_FOUND);
        }
        return vault;
    }
}
