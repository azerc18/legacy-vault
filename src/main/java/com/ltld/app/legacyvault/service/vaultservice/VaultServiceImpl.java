package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryRequest;
import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryResponse;
import com.ltld.app.legacyvault.dto.vaultdto.CreateVaultRequest;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoServiceImpl;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VaultServiceImpl implements VaultService {

    private final VaultRepository vaultRepository;
    private final DigitalAssetRepository digitalAssetRepository;
    private final UserRepository userRepository;
    private final CryptoService cryptoService;
    private final AuditLogService auditLogService;
    private final EmailSender emailSender;

    @Override
    @Transactional // Nếu có lỗi xảy ra ở bất kỳ dòng nào, DB sẽ undo lại toàn bộ (rollback)
    public Vault createVault(UUID ownerId, CreateVaultRequest request) throws Exception {
        // 1. Tìm thông tin chủ sở hữu (Owner)
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new RuntimeException("Owner not found"));

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

        return vault;
    }

    @Override
    @Transactional
    public void deleteVault(UUID vaultId, UUID ownerId) throws Exception {
        // 1. Tìm Vault
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new RuntimeException("Vault not found"));

        // 2. Bảo mật: Két của ai người nấy xóa
        if (!vault.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Unauthorized: You don't own this vault");
        }

        // 3. Nghiệp vụ: Trạng thái không phải ACTIVE thì từ chối xóa
        if (vault.getStatus() != VaultStatus.ACTIVE) {
            throw new RuntimeException("Cannot delete vault that is not in active state");
        }

        // 4. Phải xóa các tài sản (DigitalAsset) bên trong két trước
        digitalAssetRepository.findByVaultId(vaultId).forEach(digitalAssetRepository::delete);

        // 5. Sau đó mới đập cái két (Vault)
        vaultRepository.delete(vault);
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
            auditLogService.log(AuditAction.BENEFICIARY_ASSIGN_DENIED, AuditResult.FAILURE, ownerId, null,
                    "Vault", vaultId.toString(), "not owner");
            throw new VaultException("Bạn không có quyền thao tác trên Vault này", HttpStatus.FORBIDDEN);
        }

        // Vault đã bước vào quy trình bàn giao thì không được đổi người thụ hưởng (tương tự BR-08)
        if (vault.getStatus() != VaultStatus.ACTIVE) {
            auditLogService.log(AuditAction.BENEFICIARY_ASSIGN_DENIED, AuditResult.FAILURE, ownerId, owner.getEmail(),
                    "Vault", vaultId.toString(), "vault status " + vault.getStatus());
            throw new VaultException("Chỉ có thể chỉ định người thụ hưởng khi Vault đang ở trạng thái ACTIVE",
                    HttpStatus.CONFLICT);
        }

        String email = request.getEmail().trim().toLowerCase();
        if (email.equals(owner.getEmail())) {
            throw new VaultException("Không thể tự chỉ định chính mình làm người thụ hưởng", HttpStatus.BAD_REQUEST);
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

        // Chỉ liên kết ngay với tài khoản đã xác minh email; còn lại lưu email chờ và liên kết khi xác minh xong
        User beneficiary = userRepository.findByEmail(email)
                .filter(u -> u.getStatus() != UserStatus.PENDING)
                .orElse(null);
        vault.setBeneficiary(beneficiary);
        vault.setBeneficiaryEmail(beneficiary == null ? email : null);
        vaultRepository.save(vault);

        boolean pending = beneficiary == null;
        auditLogService.log(replacing ? AuditAction.BENEFICIARY_REPLACED : AuditAction.BENEFICIARY_ASSIGNED,
                AuditResult.SUCCESS, ownerId, owner.getEmail(), "Vault", vaultId.toString(),
                "beneficiary=" + email + (replacing ? ", previous=" + currentEmail : "")
                        + (pending ? ", pending registration" : ""));

        if (pending) {
            emailSender.sendBeneficiaryInvitation(email, owner.getFullName(), vault.getName());
        } else {
            emailSender.sendBeneficiaryAssignedNotice(email, owner.getFullName(), vault.getName());
        }
        emailSender.sendBeneficiaryAssignmentConfirmation(owner.getEmail(), vault.getName(), email, pending);

        return toAssignResponse(vault, email, replacing);
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
}
