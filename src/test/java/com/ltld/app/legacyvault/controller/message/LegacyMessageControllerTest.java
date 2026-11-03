package com.ltld.app.legacyvault.controller.message;

import com.ltld.app.legacyvault.controller.LegacyMessageController;
import com.ltld.app.legacyvault.dto.messagedto.CreateLegacyMessageRequest;
import com.ltld.app.legacyvault.dto.messagedto.LegacyMessageResponse;
import com.ltld.app.legacyvault.enums.LegacyMessageDeliveryTrigger;
import com.ltld.app.legacyvault.enums.LegacyMessageRecipientType;
import com.ltld.app.legacyvault.enums.LegacyMessageStatus;
import com.ltld.app.legacyvault.service.messageservice.LegacyMessageService;
import com.ltld.app.legacyvault.service.messageservice.LegacyMessageService.DecryptedAttachment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LegacyMessageController.class)
public class LegacyMessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LegacyMessageService legacyMessageService;

    @Test
    @WithMockUser(authorities = {"VAULT_CREATE", "VAULT_READ"})
    void createMessage_ReturnsCreated() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Principal mockPrincipal = () -> ownerId.toString();

        LegacyMessageResponse response = LegacyMessageResponse.builder()
                .id(messageId)
                .vaultId(vaultId)
                .title("Letter to kids")
                .content("Take care of mom")
                .recipientType(LegacyMessageRecipientType.ALL_BENEFICIARIES)
                .deliveryTrigger(LegacyMessageDeliveryTrigger.ON_CLAIM)
                .status(LegacyMessageStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .unlocked(true)
                .build();

        when(legacyMessageService.createMessage(eq(ownerId), eq(vaultId), any(CreateLegacyMessageRequest.class), any()))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/vaults/{vaultId}/messages", vaultId)
                        .param("title", "Letter to kids")
                        .param("content", "Take care of mom")
                        .param("recipientType", "ALL_BENEFICIARIES")
                        .param("deliveryTrigger", "ON_CLAIM")
                        .principal(mockPrincipal)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(messageId.toString()))
                .andExpect(jsonPath("$.data.title").value("Letter to kids"));
    }

    @Test
    @WithMockUser(authorities = {"VAULT_READ"})
    void getMessages_ReturnsList() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        Principal mockPrincipal = () -> ownerId.toString();

        LegacyMessageResponse r = LegacyMessageResponse.builder()
                .id(UUID.randomUUID())
                .vaultId(vaultId)
                .title("Letter 1")
                .content("Content 1")
                .recipientType(LegacyMessageRecipientType.ALL_BENEFICIARIES)
                .deliveryTrigger(LegacyMessageDeliveryTrigger.ON_CLAIM)
                .status(LegacyMessageStatus.ACTIVE)
                .build();

        when(legacyMessageService.getMessagesByVault(ownerId, vaultId)).thenReturn(List.of(r));

        mockMvc.perform(get("/api/vaults/{vaultId}/messages", vaultId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].title").value("Letter 1"));
    }

    @Test
    @WithMockUser(authorities = {"VAULT_DELETE"})
    void deleteMessage_ReturnsOk() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Principal mockPrincipal = () -> ownerId.toString();

        mockMvc.perform(delete("/api/vaults/{vaultId}/messages/{messageId}", vaultId, messageId)
                        .principal(mockPrincipal)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(legacyMessageService).deleteMessage(ownerId, vaultId, messageId);
    }

    @Test
    @WithMockUser(authorities = {"VAULT_READ"})
    void downloadAttachment_ReturnsFile() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Principal mockPrincipal = () -> ownerId.toString();

        DecryptedAttachment att = new DecryptedAttachment("video.mp4", "video/mp4", "video bytes".getBytes());
        when(legacyMessageService.getAttachment(ownerId, vaultId, messageId)).thenReturn(att);

        mockMvc.perform(get("/api/vaults/{vaultId}/messages/{messageId}/attachment", vaultId, messageId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "video/mp4"))
                .andExpect(content().bytes("video bytes".getBytes()));
    }
}

