package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.dto.vaultdto.*;
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
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoServiceImpl;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

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

        // 2. Bảo mật: Két của ai người nấy xóa
        if (!vault.getOwner().getId().equals(ownerId)) {
            // Tra cứu email của kẻ gọi (caller/stranger), không lấy nhầm email của chủ két
            String callerEmail = userRepository.findById(ownerId)
                    .map(User::getEmail).orElse("unknown");
            auditLogService.log(AuditAction.VAULT_DELETE_DENIED, AuditResult.FAILURE,
                    ownerId, callerEmail, vaultId, "Vault", vaultId.toString(),
                    "Unauthorized: You don't own this vault");
            throw new VaultException("Unauthorized: You don't own this vault", HttpStatus.FORBIDDEN);
        }

        String ownerEmail = vault.getOwner().getEmail();

        // 3. Nghiệp vụ: Trạng thái không phải ACTIVE thì từ chối xóa
        if (vault.getStatus() != VaultStatus.ACTIVE) {
            auditLogService.failure(AuditAction.VAULT_DELETE_DENIED, ownerId, ownerEmail, "Cannot delete vault that is not in active state");
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
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    filesToDelete.forEach(fileStorageService::deleteFile);
                }
            });
        } else {
            // Khi chạy ngoài Spring transaction (ví dụ: trong unit test)
            filesToDelete.forEach(fileStorageService::deleteFile);
        }

        auditLogService.log(AuditAction.VAULT_DELETED, AuditResult.SUCCESS,
                ownerId, ownerEmail, vault.getId(),
                "Vault", vaultId.toString(), null);
    }

    // FR-02: Owner chỉ định (hoặc thay thế) người thụ hưởng cho Vault
    @Override
    @Transactional
    public AssignBeneficiaryResponse assignBeneficiary(UUID ownerId, UUID vaultId, AssignBeneficiaryRequest request) {
        // Khóa dòng Vault để 2 request gán đồng thời không cùng vượt qua bước xác nhận ghi đè
        Vault vault = vaultRepository.findByIdForUpdate(vaultId)
                .orElseThrow(() -> new VaultException("Không tìm thấy Vault", HttpStatus.NOT_FOUND));

        User owner = vault.getOwner();
        if (!owner.getId().equals(ownerId)) {
            auditLogService.log(AuditAction.BENEFICIARY_ASSIGN_DENIED, AuditResult.FAILURE, ownerId, null, vaultId,
                    "Vault", vaultId.toString(), "not owner");
            throw new VaultException("Bạn không có quyền thao tác trên Vault này", HttpStatus.FORBIDDEN);
        }

        // Vault đã bước vào quy trình bàn giao thì không được đổi người thụ hưởng (tương tự BR-08)
        if (vault.getStatus() != VaultStatus.ACTIVE) {
            auditLogService.log(AuditAction.BENEFICIARY_ASSIGN_DENIED, AuditResult.FAILURE, ownerId, owner.getEmail(), vaultId,
                    "Vault", vaultId.toString(), "vault status " + vault.getStatus());
            throw new VaultException("Chỉ có thể chỉ định người thụ hưởng khi Vault đang ở trạng thái ACTIVE",
                    HttpStatus.CONFLICT);
        }

        String email = request.getEmail().trim().toLowerCase();
        if (email.equals(owner.getEmail())) {
            throw new VaultException("Không thể tự chỉ định chính mình làm người thụ hưởng", HttpStatus.BAD_REQUEST);
        }

        // Q26: Executor (chưa bị thu hồi) của Owner không được đồng thời là người thụ hưởng.
        // Đặt trước bước "gán lại đúng người" để cả trường hợp người thụ hưởng hiện tại vừa trở thành Executor cũng bị chặn.
        if (executorAssignmentRepository.existsLiveExecutorWithEmail(ownerId, email)) {
            throw new VaultException("Người này đang là Digital Executor của bạn, không thể đồng thời là người thụ hưởng.",
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
            throw new VaultException("Vault đã có người thụ hưởng (" + currentEmail
                    + "). Gửi lại với confirmReplace=true để thay thế.", HttpStatus.CONFLICT);
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
                        .createdAt(vault.getCreatedAt())
                        .updatedAt(vault.getUpdatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VaultDetailResponse getVaultDetail(UUID vaultId, UUID requesterId, boolean isAdmin) {
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new VaultException("Vault not found", HttpStatus.NOT_FOUND));

        if (!isAdmin && !vault.getOwner().getId().equals(requesterId)) {
            throw new VaultException("Unauthorized: You don't have permission to access this vault", HttpStatus.FORBIDDEN);
        }

        int docCount = legalDocumentRepository.countByVaultId(vaultId);
        int assetCount = (int) digitalAssetRepository.countByVaultIdAndStatus(vaultId, com.ltld.app.legacyvault.enums.AssetStatus.ACTIVE);

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

    @Override
    @Transactional
    public VaultResponse updateVault(UUID vaultId, UUID ownerId, UpdateVaultRequest request) {
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new VaultException("Vault not found", HttpStatus.NOT_FOUND));

        if (!vault.getOwner().getId().equals(ownerId)) {
            throw new VaultException("Unauthorized: You don't own this vault", HttpStatus.FORBIDDEN);
        }

        if (vault.getStatus() != com.ltld.app.legacyvault.enums.VaultStatus.ACTIVE) {
            throw new VaultException("Cannot update vault that is not in active state", HttpStatus.BAD_REQUEST);
        }

        vault.setName(request.getName());
        vault.setDescription(request.getDescription());
        Vault updated = vaultRepository.save(vault);

        return VaultResponse.builder()
                .id(updated.getId())
                .name(updated.getName())
                .description(updated.getDescription())
                .status(updated.getStatus())
                .createdAt(updated.getCreatedAt())
                .updatedAt(updated.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<VaultResponse> getAllVaultsForAdmin(com.ltld.app.legacyvault.enums.VaultStatus status, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<Vault> page = (status != null)
                ? vaultRepository.findByStatus(status, pageable)
                : vaultRepository.findAll(pageable);

        return page.map(v -> VaultResponse.builder()
                .id(v.getId())
                .name(v.getName())
                .description(v.getDescription())
                .status(v.getStatus())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build());
    }

}
