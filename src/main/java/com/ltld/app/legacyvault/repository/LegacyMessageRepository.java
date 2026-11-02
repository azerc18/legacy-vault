package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.LegacyMessage;
import com.ltld.app.legacyvault.enums.LegacyMessageDeliveryTrigger;
import com.ltld.app.legacyvault.enums.LegacyMessageRecipientType;
import com.ltld.app.legacyvault.enums.LegacyMessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LegacyMessageRepository extends JpaRepository<LegacyMessage, UUID> {

    List<LegacyMessage> findByVaultIdOrderByCreatedAtDesc(UUID vaultId);

    Optional<LegacyMessage> findByIdAndVaultId(UUID id, UUID vaultId);

    @Query("SELECT m FROM LegacyMessage m WHERE m.vault.id = :vaultId " +
           "AND (m.recipientType = :allType OR (m.recipientBeneficiary IS NOT NULL AND m.recipientBeneficiary.id = :beneficiaryId)) " +
           "ORDER BY m.createdAt DESC")
    List<LegacyMessage> findForBeneficiary(
            @Param("vaultId") UUID vaultId,
            @Param("beneficiaryId") UUID beneficiaryId,
            @Param("allType") LegacyMessageRecipientType allType);

    List<LegacyMessage> findByStatusAndDeliveryTriggerAndDeliverDateLessThanEqual(
            LegacyMessageStatus status,
            LegacyMessageDeliveryTrigger trigger,
            LocalDate date);
}

