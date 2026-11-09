package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.RefreshToken;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.ExecutorTaskRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.RefreshTokenRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Đọc dữ liệu lazy trong transaction riêng cho AuditMailListener (listener chạy @Async, ngoài transaction). */
@Service
@RequiredArgsConstructor
public class MailDataReader {

    private static final int MAX_DEVICE_LENGTH = 150;

    public record Recipient(String email, String fullName) {
    }

    public record VaultRecipients(String vaultName, List<Recipient> recipients) {
    }

    public record CompletedTask(String vaultName, String platform, List<Recipient> recipients) {
    }

    public record ReopenedRequest(String executorEmail, String ownerLabel) {
    }

    private final RefreshTokenRepository refreshTokenRepository;
    private final DigitalAssetRepository digitalAssetRepository;
    private final VaultRepository vaultRepository;
    private final VaultBeneficiaryRepository vaultBeneficiaryRepository;
    private final ExecutorTaskRepository executorTaskRepository;
    private final LegalVerificationRequestRepository requestRepository;

    /**
     * N6: thiết bị của lần đăng nhập mới nhất nếu tài khoản đã từng đăng nhập nhưng chưa bao giờ từ thiết bị này.
     * So User-Agent thay vì IP vì sau proxy của Azure getRemoteAddr không phải IP thật của người dùng.
     * shortcut: User-Agent chỉ phân biệt loại trình duyệt và hệ điều hành, cần chính xác hơn thì dùng cookie thiết bị.
     */
    @Transactional(readOnly = true)
    public Optional<String> newDeviceOf(UUID userId) {
        Optional<RefreshToken> latest = refreshTokenRepository.findFirstByUser_IdOrderByCreatedAtDesc(userId);
        if (latest.isEmpty() || latest.get().getUserAgent() == null) {
            return Optional.empty();
        }
        String agent = latest.get().getUserAgent();
        List<String> earlier = refreshTokenRepository.findEarlierUserAgents(userId, latest.get().getCreatedAt());
        String family = withoutVersions(agent);
        if (earlier.isEmpty() || earlier.stream().anyMatch(a -> withoutVersions(a).equals(family))) {
            return Optional.empty();
        }
        return Optional.of(agent.length() > MAX_DEVICE_LENGTH ? agent.substring(0, MAX_DEVICE_LENGTH) : agent);
    }

    // Trình duyệt tự cập nhật làm đổi số phiên bản, không phải đổi thiết bị
    static String withoutVersions(String userAgent) {
        return userAgent == null ? "" : userAgent.replaceAll("[0-9._]+", "");
    }

    @Transactional(readOnly = true)
    public Optional<String> assetName(UUID assetId) {
        return digitalAssetRepository.findById(assetId).map(DigitalAsset::getAssetName);
    }

    @Transactional(readOnly = true)
    public Optional<VaultRecipients> vaultRecipients(UUID vaultId) {
        return vaultRepository.findById(vaultId).map(Vault::getName)
                .map(name -> new VaultRecipients(name, liveBeneficiaries(vaultId, false)));
    }

    @Transactional(readOnly = true)
    public Optional<CompletedTask> completedTask(UUID taskId) {
        return executorTaskRepository.findById(taskId).map(t -> new CompletedTask(t.getVault().getName(),
                t.getPlatform(), liveBeneficiaries(t.getVault().getId(), true)));
    }

    @Transactional(readOnly = true)
    public Optional<ReopenedRequest> reopenedRequest(UUID requestId) {
        return requestRepository.findById(requestId).map(MailDataReader::toReopened);
    }

    private static ReopenedRequest toReopened(LegalVerificationRequest r) {
        User owner = r.getOwner();
        String label = owner.getFullName() == null || owner.getFullName().isBlank()
                ? owner.getEmail() : owner.getFullName() + " (" + owner.getEmail() + ")";
        return new ReopenedRequest(r.getExecutor().getEmail(), label);
    }

    // Bỏ người đã từ chối / nhường quyền; linkedOnly: chỉ người đã có tài khoản (mới vào xác nhận việc được)
    private List<Recipient> liveBeneficiaries(UUID vaultId, boolean linkedOnly) {
        return vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId).stream()
                .filter(b -> b.getStatus() != BeneficiaryInviteStatus.DECLINED
                        && b.getStatus() != BeneficiaryInviteStatus.WAIVED)
                .filter(b -> !linkedOnly || b.getUser() != null)
                .map(MailDataReader::toRecipient)
                .toList();
    }

    private static Recipient toRecipient(VaultBeneficiary b) {
        return new Recipient(b.getEmail(), b.getFullName());
    }
}
