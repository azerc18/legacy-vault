package com.ltld.app.legacyvault.legalverificationtest;

import com.ltld.app.legacyvault.dto.RejectVerificationRequestDto;
import com.ltld.app.legacyvault.dto.SignVerificationRequestDto;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VerificationRequestVault;
import com.ltld.app.legacyvault.enums.SignatureMethod;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import com.ltld.app.legacyvault.exception.RequestAlreadyReviewedException;
import com.ltld.app.legacyvault.exception.VerificationRequestNotFoundException;
import com.ltld.app.legacyvault.repository.DigitalSignatureRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
import com.ltld.app.legacyvault.service.impl.LegalVerificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LegalVerificationServiceImplTest {

    @Mock
    private LegalVerificationRequestRepository requestRepository;

    @Mock
    private DigitalSignatureRepository signatureRepository;

    @Mock
    private VerificationRequestVaultRepository requestVaultRepository;

    @InjectMocks
    private LegalVerificationServiceImpl service;

    private UUID requestId;
    private UUID verifierId;
    private LegalVerificationRequest pendingRequest;

    @BeforeEach
    void setUp() {
        requestId = UUID.randomUUID();
        verifierId = UUID.randomUUID();

        pendingRequest = LegalVerificationRequest.builder()
                .id(requestId)
                .owner(User.builder().fullName("Test Owner").build())
                .executor(User.builder().fullName("Test Executor").build())
                .deathCertificateFileUrlEncrypted("enc://test.pdf")
                .status(VerificationStatus.PENDING)
                .build();
    }

    // ---------- reject ----------

    @Test
    void reject_success_setsStatusRejectedAndSavesReason() {
        RejectVerificationRequestDto dto = new RejectVerificationRequestDto();
        dto.setReason("Giấy chứng tử không hợp lệ");

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
        when(requestRepository.save(any(LegalVerificationRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.reject(requestId, verifierId, dto);

        assertThat(pendingRequest.getStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(pendingRequest.getRejectionReason()).isEqualTo("Giấy chứng tử không hợp lệ");
        assertThat(pendingRequest.getDecidedAt()).isNotNull();
    }

    @Test
    void reject_requestNotFound_throwsNotFoundException() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reject(requestId, verifierId, new RejectVerificationRequestDto()))
                .isInstanceOf(VerificationRequestNotFoundException.class);

        verify(requestRepository, never()).save(any());
    }

    @Test
    void reject_alreadyApproved_throwsAlreadyReviewedException() {
        pendingRequest.setStatus(VerificationStatus.APPROVED);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.reject(requestId, verifierId, new RejectVerificationRequestDto()))
                .isInstanceOf(RequestAlreadyReviewedException.class);

        verify(requestRepository, never()).save(any());
    }

    // ---------- approveAndSign ----------

    @Test
    void approveAndSign_success_approvesRequestSavesSignatureAndUnlocksVault() {
        Vault vault = Vault.builder().status(VaultStatus.ACTIVE).build();
        VerificationRequestVault link = VerificationRequestVault.builder()
                .request(pendingRequest)
                .vault(vault)
                .build();

        SignVerificationRequestDto dto = new SignVerificationRequestDto();
        dto.setMethod(SignatureMethod.MOCK_OTP);
        dto.setCode("123456");

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
        when(requestVaultRepository.findByRequestId(requestId)).thenReturn(List.of(link));
        when(requestRepository.save(any(LegalVerificationRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.approveAndSign(requestId, verifierId, dto);

        assertThat(pendingRequest.getStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(vault.getStatus()).isEqualTo(VaultStatus.UNLOCKED);
        assertThat(vault.getUnlockedAt()).isNotNull();
        verify(signatureRepository, times(1)).save(any());
    }

    @Test
    void approveAndSign_alreadyRejected_throwsAndDoesNotSignOrUnlock() {
        pendingRequest.setStatus(VerificationStatus.REJECTED);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

        SignVerificationRequestDto dto = new SignVerificationRequestDto();
        dto.setMethod(SignatureMethod.MOCK_OTP);
        dto.setCode("123456");

        assertThatThrownBy(() -> service.approveAndSign(requestId, verifierId, dto))
                .isInstanceOf(RequestAlreadyReviewedException.class);

        verify(signatureRepository, never()).save(any());
        verify(requestVaultRepository, never()).findByRequestId(any());
    }
}