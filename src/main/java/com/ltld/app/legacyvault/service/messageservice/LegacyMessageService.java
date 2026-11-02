package com.ltld.app.legacyvault.service.messageservice;

import com.ltld.app.legacyvault.dto.messagedto.CreateLegacyMessageRequest;
import com.ltld.app.legacyvault.dto.messagedto.LegacyMessageResponse;
import com.ltld.app.legacyvault.dto.messagedto.UpdateLegacyMessageRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface LegacyMessageService {

    LegacyMessageResponse createMessage(UUID ownerId, UUID vaultId, CreateLegacyMessageRequest request, MultipartFile attachment) throws Exception;

    List<LegacyMessageResponse> getMessagesByVault(UUID ownerId, UUID vaultId);

    LegacyMessageResponse getMessageById(UUID ownerId, UUID vaultId, UUID messageId) throws Exception;

    LegacyMessageResponse updateMessage(UUID ownerId, UUID vaultId, UUID messageId, UpdateLegacyMessageRequest request, MultipartFile attachment) throws Exception;

    void deleteMessage(UUID ownerId, UUID vaultId, UUID messageId);

    DecryptedAttachment getAttachment(UUID ownerId, UUID vaultId, UUID messageId) throws Exception;

    List<LegacyMessageResponse> getMessagesForBeneficiary(UUID userId, UUID vaultId);

    LegacyMessageResponse getMessageDetailForBeneficiary(UUID userId, UUID vaultId, UUID messageId) throws Exception;

    DecryptedAttachment getAttachmentForBeneficiary(UUID userId, UUID vaultId, UUID messageId) throws Exception;

    record DecryptedAttachment(String filename, String contentType, byte[] data) {}
}

