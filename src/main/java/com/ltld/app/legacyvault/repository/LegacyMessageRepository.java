package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.LegacyMessage;
import com.ltld.app.legacyvault.enums.LegacyMessageDeliveryTrigger;
import com.ltld.app.legacyvault.enums.LegacyMessageRecipientType;
import com.ltld.app.legacyvault.enums.LegacyMessageStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LegacyMessageRepository extends JpaRepository<LegacyMessage, UUID> {

    List<LegacyMessage> findByVaultIdOrderByCreatedAtDesc(UUID vaultId);

    Optional<LegacyMessage> findByIdAndVaultId(UUID id, UUID vaultId);

    @Query("SELECT m FROM LegacyMessage m WHERE m.vault.id = :vaultId AND " +
           "(m.recipientType = :allType OR m.recipientBeneficiary.id = :beneficiaryId) " +
           "ORDER BY m.createdAt DESC")
    List<LegacyMessage> findForBeneficiary(
            @Param("vaultId") UUID vaultId,
            @Param("beneficiaryId") UUID beneficiaryId,
            @Param("allType") LegacyMessageRecipientType allType);

    // T1 (m7): tổng dung lượng đính kèm lời nhắn của mọi vault thuộc Owner, cộng vào quota gói cùng tài liệu
    @Query("SELECT COALESCE(SUM(m.attachmentSize), 0) FROM LegacyMessage m WHERE m.vault.owner.id = :ownerId")
    long sumAttachmentSizeByOwnerId(@Param("ownerId") UUID ownerId);

    /** Việc 2: số lời nhắn di sản của Owner (trang Billing hiện mức sử dụng). */
    @Query("SELECT COUNT(m) FROM LegacyMessage m WHERE m.vault.owner.id = :ownerId")
    long countByOwnerId(@Param("ownerId") UUID ownerId);

    List<LegacyMessage> findByVaultIdAndStatus(UUID vaultId, LegacyMessageStatus status);

    @Query("SELECT m FROM LegacyMessage m JOIN FETCH m.vault v " +
           "WHERE m.status = :status AND v.status IN :vaultStatuses " +
           "AND (m.deliveryTrigger = :onClaimTrigger OR (m.deliveryTrigger = :specificDateTrigger AND m.deliverDate <= :today))")
    List<LegacyMessage> findDueMessagesForUnlockedVaults(
            @Param("status") LegacyMessageStatus status,
            @Param("vaultStatuses") Collection<VaultStatus> vaultStatuses,
            @Param("onClaimTrigger") LegacyMessageDeliveryTrigger onClaimTrigger,
            @Param("specificDateTrigger") LegacyMessageDeliveryTrigger specificDateTrigger,
            @Param("today") LocalDate today);

    // F4b: Chuyển trạng thái ACTIVE -> DELIVERED nguyên tử để ngăn race condition giữa Listener và Scheduler
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE LegacyMessage m
            SET m.status = com.ltld.app.legacyvault.enums.LegacyMessageStatus.DELIVERED, m.deliveredAt = :now
            WHERE m.id = :id AND m.status = com.ltld.app.legacyvault.enums.LegacyMessageStatus.ACTIVE
            """)
    int markDelivered(@Param("id") UUID id, @Param("now") LocalDateTime now);
}
