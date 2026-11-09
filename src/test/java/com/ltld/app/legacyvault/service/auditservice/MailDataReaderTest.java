package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.entity.ExecutorTask;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.repository.ExecutorTaskRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.RefreshTokenRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailDataReaderTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Mock private ExecutorTaskRepository executorTaskRepository;
    @Mock private LegalVerificationRequestRepository requestRepository;
    @InjectMocks private MailDataReader reader;

    private final UUID vaultId = UUID.randomUUID();

    private Vault vault() {
        return Vault.builder().id(vaultId).name("Kho").build();
    }

    private static VaultBeneficiary beneficiary(String email, User user, BeneficiaryInviteStatus status) {
        return VaultBeneficiary.builder().email(email).fullName(email).user(user).status(status).build();
    }

    private void stubBeneficiaries() {
        when(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId)).thenReturn(List.of(
                beneficiary("linked@example.com", new User(), BeneficiaryInviteStatus.ACCEPTED),
                beneficiary("pending@example.com", null, BeneficiaryInviteStatus.PENDING_REGISTRATION),
                beneficiary("declined@example.com", new User(), BeneficiaryInviteStatus.DECLINED),
                beneficiary("waived@example.com", new User(), BeneficiaryInviteStatus.WAIVED)));
    }

    @Test
    void vaultRecipients_skipsDeclinedAndWaived_keepsNotYetRegistered() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault()));
        stubBeneficiaries();

        MailDataReader.VaultRecipients result = reader.vaultRecipients(vaultId).orElseThrow();

        assertThat(result.vaultName()).isEqualTo("Kho");
        assertThat(result.recipients()).extracting(MailDataReader.Recipient::email)
                .containsExactly("linked@example.com", "pending@example.com");
    }

    @Test
    void vaultRecipients_unknownVault_isEmpty() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.empty());

        assertThat(reader.vaultRecipients(vaultId)).isEmpty();
    }

    @Test
    void completedTask_onlyBeneficiariesWhoCanSignIn() {
        UUID taskId = UUID.randomUUID();
        when(executorTaskRepository.findById(taskId)).thenReturn(Optional.of(
                ExecutorTask.builder().id(taskId).vault(vault()).platform("Facebook").build()));
        stubBeneficiaries();

        MailDataReader.CompletedTask result = reader.completedTask(taskId).orElseThrow();

        assertThat(result.platform()).isEqualTo("Facebook");
        assertThat(result.recipients()).extracting(MailDataReader.Recipient::email).containsExactly("linked@example.com");
    }

    @Test
    void reopenedRequest_ownerWithoutName_usesEmail() {
        UUID id = UUID.randomUUID();
        LegalVerificationRequest request = LegalVerificationRequest.builder().id(id)
                .owner(User.builder().email("owner@example.com").build())
                .executor(User.builder().email("exec@example.com").build()).build();
        when(requestRepository.findById(id)).thenReturn(Optional.of(request));

        assertThat(reader.reopenedRequest(id)).contains(
                new MailDataReader.ReopenedRequest("exec@example.com", "owner@example.com"));
    }

    @Test
    void newDevice_noSessions_isEmpty() {
        UUID userId = UUID.randomUUID();
        when(refreshTokenRepository.findFirstByUser_IdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());

        assertThat(reader.newDeviceOf(userId)).isEmpty();
        verifyNoMoreInteractions(vaultRepository);
    }

    @Test
    void withoutVersions_ignoresBrowserUpdates_keepsDifferentBrowsers() {
        assertThat(MailDataReader.withoutVersions("Chrome/130.0.1.2")).isEqualTo(MailDataReader.withoutVersions("Chrome/131.5"));
        assertThat(MailDataReader.withoutVersions("Chrome/130")).isNotEqualTo(MailDataReader.withoutVersions("Firefox/130"));
        assertThat(MailDataReader.withoutVersions(null)).isEmpty();
    }
}
