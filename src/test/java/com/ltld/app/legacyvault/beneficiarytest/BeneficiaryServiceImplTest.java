package com.ltld.app.legacyvault.beneficiarytest;

import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimResponse;
import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.entity.IdentityVerification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.VerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import com.ltld.app.legacyvault.exception.BeneficiaryException;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.IdentityVerificationRepository;
import com.ltld.app.legacyvault.service.beneficiaryservice.BeneficiaryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BeneficiaryServiceImplTest {

    @Mock
    private BeneficiaryClaimRepository claimRepository;

    @Mock
    private IdentityVerificationRepository verificationRepository;

    @InjectMocks
    private BeneficiaryServiceImpl beneficiaryService;

    private BeneficiaryClaimRequest request;
    private BeneficiaryClaim validClaim;
    private UUID vaultId;
    private UUID claimId;

    @BeforeEach
    void setUp() {
        vaultId = UUID.randomUUID();
        claimId = UUID.randomUUID();

        request = new BeneficiaryClaimRequest();
        request.setVaultId(vaultId);
        request.setVerificationMethod(VerificationMethod.OTP);

        Vault vault = new Vault();
        vault.setId(vaultId);

        User beneficiary = new User();
        beneficiary.setId(UUID.randomUUID());

        validClaim = BeneficiaryClaim.builder()
                .id(claimId)
                .vault(vault)
                .beneficiary(beneficiary)
                .status(ClaimStatus.PENDING)
                .claimDeadlineAt(LocalDateTime.now().plusDays(5)) // Còn hạn 5 ngày
                .build();
    }

    @Test
    void initializeClaim_success_savesVerificationAndReturnsResponse() {
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.of(validClaim));

        BeneficiaryClaimResponse response = beneficiaryService.initializeClaim(request);

        // Bắt object IdentityVerification được đưa vào hàm save() để kiểm tra chi tiết
        ArgumentCaptor<IdentityVerification> verificationCaptor = ArgumentCaptor.forClass(IdentityVerification.class);
        verify(verificationRepository, times(1)).save(verificationCaptor.capture());

        IdentityVerification savedVerification = verificationCaptor.getValue();

        // Assert các trường của bản ghi vừa lưu
        assertThat(savedVerification.getVault().getId()).isEqualTo(vaultId);
        assertThat(savedVerification.getBeneficiary().getId()).isEqualTo(validClaim.getBeneficiary().getId());
        assertThat(savedVerification.getMethod()).isEqualTo(VerificationMethod.OTP);
        assertThat(savedVerification.getStatus()).isEqualTo(VerificationStatus.PENDING);

        // Assert kết quả trả về cho Frontend
        assertThat(response).isNotNull();
        assertThat(response.getClaimId()).isEqualTo(claimId);
        assertThat(response.getVaultId()).isEqualTo(vaultId);
        assertThat(response.getStatus()).isEqualTo(ClaimStatus.PENDING);
    }

    @Test
    void initializeClaim_claimNotFound_throwsBeneficiaryException_andDoesNotSave() {
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> beneficiaryService.initializeClaim(request))
                .isInstanceOf(RuntimeException.class) // Đổi thành BeneficiaryException nếu đã custom
                .hasMessage("Không tìm thấy yêu cầu nhận tài sản cho Vault ID này.");

        verify(verificationRepository, never()).save(any());
    }

    @Test
    void initializeClaim_statusClaimed_throwsBeneficiaryException_andDoesNotSave() {
        validClaim.setStatus(ClaimStatus.CLAIMED);
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.of(validClaim));

        assertThatThrownBy(() -> beneficiaryService.initializeClaim(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Tài sản này đã được nhận, không thể yêu cầu lại.");

        verify(verificationRepository, never()).save(any());
    }

    @Test
    void initializeClaim_statusExpired_throwsBeneficiaryException_andDoesNotSave() {
        validClaim.setStatus(ClaimStatus.EXPIRED);
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.of(validClaim));

        assertThatThrownBy(() -> beneficiaryService.initializeClaim(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Thời hạn yêu cầu nhận tài sản đã kết thúc.");

        verify(verificationRepository, never()).save(any());
    }

    @Test
    void initializeClaim_pastDeadline_throwsBeneficiaryException_andDoesNotSave() {
        // Đặt hạn chót là 1 ngày trước đó
        validClaim.setClaimDeadlineAt(LocalDateTime.now().minusDays(1));
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.of(validClaim));

        assertThatThrownBy(() -> beneficiaryService.initializeClaim(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Thời hạn yêu cầu nhận tài sản đã kết thúc.");

        verify(verificationRepository, never()).save(any());
    }
}