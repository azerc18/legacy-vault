package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.IdentityVerification;
import com.ltld.app.legacyvault.enums.VerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, UUID> {

    Optional<IdentityVerification>
    findFirstByVaultIdAndBeneficiaryIdAndMethodAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID vaultId, UUID beneficiaryId, VerificationMethod method,
            VerificationStatus status, LocalDateTime createdAfter);

    // FR-17: phiên xác thực mới nhất theo vault + beneficiary + method + trạng thái
    Optional<IdentityVerification>
    findFirstByVaultIdAndBeneficiaryIdAndMethodAndStatusOrderByCreatedAtDesc(
            UUID vaultId, UUID beneficiaryId, VerificationMethod method, VerificationStatus status);

    // FR-17: có phiên nào ở trạng thái này không (dùng để kiểm tra "đã bị khóa" với FAILED)
    boolean existsByVaultIdAndBeneficiaryIdAndStatus(
            UUID vaultId, UUID beneficiaryId, VerificationStatus status);

    // FR-17: còn phiên xem hợp lệ không (SUCCESS và verified_at sau mốc thời gian)
    boolean existsByVaultIdAndBeneficiaryIdAndStatusAndVerifiedAtAfter(
            UUID vaultId, UUID beneficiaryId, VerificationStatus status, LocalDateTime verifiedAfter);

    // FR-17: tăng bộ đếm NGUYÊN TỬ trong DB (UPDATE tự khóa dòng), tránh lost update khi nhiều request song song.
    // COALESCE vì cột attempt_count cho phép NULL.
    @Modifying
    @Query("UPDATE IdentityVerification v SET v.attemptCount = COALESCE(v.attemptCount, 0) + 1 WHERE v.id = :id")
    void incrementAttemptCount(@Param("id") UUID id);

    // FR-17: đọc lại giá trị đếm sau khi tăng
    @Query("SELECT COALESCE(v.attemptCount, 0) FROM IdentityVerification v WHERE v.id = :id")
    int findAttemptCountById(@Param("id") UUID id);

    /**
     * FR-17: cộng lượt thử của các phiên PENDING khác (cùng vault, beneficiary) tạo trong 24h gần nhất,
     * để tạo phiên mới không reset được giới hạn. Thực tế là giới hạn 5 lần sai / 24h,
     * không phải khóa vĩnh viễn ngay từ lần đầu. Chấp nhận với OTP 6 số.
     */
    @Query("SELECT COALESCE(SUM(v.attemptCount), 0) FROM IdentityVerification v " +
            "WHERE v.vault.id = :vaultId AND v.beneficiary.id = :beneficiaryId " +
            "AND v.status = com.ltld.app.legacyvault.enums.VerificationStatus.PENDING " +
            "AND v.id <> :excludeId AND v.createdAt > :after")
    int sumAttemptsOfOtherPendingSessions(@Param("vaultId") UUID vaultId,
                                          @Param("beneficiaryId") UUID beneficiaryId,
                                          @Param("excludeId") UUID excludeId,
                                          @Param("after") LocalDateTime after);

}