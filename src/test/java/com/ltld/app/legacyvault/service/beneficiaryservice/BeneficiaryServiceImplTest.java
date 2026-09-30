package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimResponse;
import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.entity.IdentityVerification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.enums.VerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import com.ltld.app.legacyvault.exception.BeneficiaryException;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.IdentityVerificationRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BeneficiaryServiceImplTest {

    @Mock
    private VaultRepository vaultRepository;
    @Mock
    private BeneficiaryClaimRepository claimRepository;
    @Mock
    private IdentityVerificationRepository verificationRepository;

    @InjectMocks
    private BeneficiaryServiceImpl beneficiaryService;
    private BeneficiaryClaim claim(ClaimStatus status, LocalDateTime deadline) {
        return BeneficiaryClaim.builder()
                .id(UUID.randomUUID()).vault(unlockedVault)
                .status(status).claimDeadlineAt(deadline).build();
    }
    private BeneficiaryClaimRequest request;
    private Vault unlockedVault;
    private User currentBeneficiary;
    private UUID vaultId;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        vaultId = UUID.randomUUID();
        currentUserId = UUID.randomUUID();

        request = new BeneficiaryClaimRequest();
        request.setVaultId(vaultId);
        request.setVerificationMethod(VerificationMethod.OTP);

        currentBeneficiary = new User();
        currentBeneficiary.setId(currentUserId);

        unlockedVault = new Vault();
        unlockedVault.setId(vaultId);
        unlockedVault.setBeneficiary(currentBeneficiary);
        unlockedVault.setStatus(VaultStatus.UNLOCKED);
    }

    @Test
    void initializeClaim_wrongBeneficiary_throwsBeneficiaryException_404() {
        // Giả lập Vault thuộc về một người khác
        User differentUser = new User();
        differentUser.setId(UUID.randomUUID());
        unlockedVault.setBeneficiary(differentUser);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));

        BeneficiaryException ex = assertThrows(BeneficiaryException.class, () ->
                beneficiaryService.initializeClaim(request, currentUserId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Không tìm thấy yêu cầu nhận tài sản.", ex.getMessage());
    }

    @Test
    void initializeClaim_vaultNotUnlocked_throwsBeneficiaryException_400() {
        unlockedVault.setStatus(VaultStatus.ACTIVE); // Vault chưa sẵn sàng
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));

        BeneficiaryException ex = assertThrows(BeneficiaryException.class, () ->
                beneficiaryService.initializeClaim(request, currentUserId));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Tài sản chưa sẵn sàng để nhận.", ex.getMessage());
    }

    @Test
    void initializeClaim_success_reusesExistingPendingVerification() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));

        // Giả lập đã có sẵn Claim
        BeneficiaryClaim existingClaim = BeneficiaryClaim.builder()
                .id(UUID.randomUUID())
                .vault(unlockedVault)
                .status(ClaimStatus.PENDING)
                .claimDeadlineAt(LocalDateTime.now().plusDays(5))
                .build();
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.of(existingClaim));

        // Giả lập đã có sẵn 1 phiên xác thực PENDING
        IdentityVerification pendingVerification = IdentityVerification.builder()
                .id(UUID.randomUUID())
                .vault(unlockedVault)
                .status(VerificationStatus.PENDING)
                .build();
        when(verificationRepository.findFirstByVaultIdAndBeneficiaryIdAndStatusAndCreatedAtAfter(
                any(), any(), any(), any())).thenReturn(Optional.of(pendingVerification));
        BeneficiaryClaimResponse response = beneficiaryService.initializeClaim(request, currentUserId);

        // Kiểm tra xem hàm save của verification có KHÔNG bị gọi thêm lần nào (tái sử dụng thành công)
        verify(verificationRepository, never()).save(any());
        assertThat(response.getVerificationId()).isEqualTo(pendingVerification.getId());
    }

    @Test
    void initializeClaim_vaultNotFound_throws404() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.empty());

        BeneficiaryException ex = assertThrows(BeneficiaryException.class,
                () -> beneficiaryService.initializeClaim(request, currentUserId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void initializeClaim_vaultHasNoBeneficiary_throws404() {
        unlockedVault.setBeneficiary(null);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));

        BeneficiaryException ex = assertThrows(BeneficiaryException.class,
                () -> beneficiaryService.initializeClaim(request, currentUserId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void initializeClaim_alreadyClaimed_throws409() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));
        when(claimRepository.findByVaultId(vaultId))
                .thenReturn(Optional.of(claim(ClaimStatus.CLAIMED, LocalDateTime.now().plusDays(5))));

        BeneficiaryException ex = assertThrows(BeneficiaryException.class,
                () -> beneficiaryService.initializeClaim(request, currentUserId));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    void initializeClaim_statusExpired_throws410() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));
        when(claimRepository.findByVaultId(vaultId))
                .thenReturn(Optional.of(claim(ClaimStatus.EXPIRED, LocalDateTime.now().plusDays(5))));

        BeneficiaryException ex = assertThrows(BeneficiaryException.class,
                () -> beneficiaryService.initializeClaim(request, currentUserId));

        assertEquals(HttpStatus.GONE, ex.getStatus());
    }

    @Test
    void initializeClaim_deadlinePassedButStatusPending_throws410() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));
        when(claimRepository.findByVaultId(vaultId))
                .thenReturn(Optional.of(claim(ClaimStatus.PENDING, LocalDateTime.now().minusMinutes(1))));

        BeneficiaryException ex = assertThrows(BeneficiaryException.class,
                () -> beneficiaryService.initializeClaim(request, currentUserId));

        assertEquals(HttpStatus.GONE, ex.getStatus());
    }

    @Test
    void initializeClaim_noClaim_createsClaimWithVaultDeadlineAndNewVerification() {
        LocalDateTime deadline = LocalDateTime.now().plusDays(10);
        unlockedVault.setClaimDeadlineAt(deadline);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.empty());
        when(claimRepository.save(any(BeneficiaryClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(verificationRepository.findFirstByVaultIdAndBeneficiaryIdAndStatusAndCreatedAtAfter(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(verificationRepository.save(any(IdentityVerification.class))).thenAnswer(inv -> inv.getArgument(0));

        beneficiaryService.initializeClaim(request, currentUserId);

        ArgumentCaptor<BeneficiaryClaim> claimCaptor = ArgumentCaptor.forClass(BeneficiaryClaim.class);
        verify(claimRepository).save(claimCaptor.capture());
        assertThat(claimCaptor.getValue().getStatus()).isEqualTo(ClaimStatus.PENDING);
        assertThat(claimCaptor.getValue().getClaimDeadlineAt()).isEqualTo(deadline);
        assertThat(claimCaptor.getValue().getBeneficiary()).isSameAs(currentBeneficiary);

        ArgumentCaptor<IdentityVerification> verCaptor = ArgumentCaptor.forClass(IdentityVerification.class);
        verify(verificationRepository).save(verCaptor.capture());
        assertThat(verCaptor.getValue().getStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(verCaptor.getValue().getMethod()).isEqualTo(VerificationMethod.OTP);
        assertThat(verCaptor.getValue().getVault()).isSameAs(unlockedVault);
    }

    @Test
    void initializeClaim_noClaimAndNoVaultDeadline_deadlineIsUnlockedAtPlus60Days() {
        LocalDateTime unlockedAt = LocalDateTime.now().minusDays(10);
        unlockedVault.setUnlockedAt(unlockedAt);
        unlockedVault.setClaimDeadlineAt(null);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.empty());
        when(claimRepository.save(any(BeneficiaryClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(verificationRepository.findFirstByVaultIdAndBeneficiaryIdAndStatusAndCreatedAtAfter(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(verificationRepository.save(any(IdentityVerification.class))).thenAnswer(inv -> inv.getArgument(0));

        beneficiaryService.initializeClaim(request, currentUserId);

        ArgumentCaptor<BeneficiaryClaim> claimCaptor = ArgumentCaptor.forClass(BeneficiaryClaim.class);
        verify(claimRepository).save(claimCaptor.capture());
        assertThat(claimCaptor.getValue().getClaimDeadlineAt()).isEqualTo(unlockedAt.plusDays(60));
    }

    @Test
    void initializeClaim_noClaimButVaultUnlockedOver60DaysAgo_throws410() {
        unlockedVault.setUnlockedAt(LocalDateTime.now().minusDays(70));
        unlockedVault.setClaimDeadlineAt(null);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault));
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.empty());
        when(claimRepository.save(any(BeneficiaryClaim.class))).thenAnswer(inv -> inv.getArgument(0));

        BeneficiaryException ex = assertThrows(BeneficiaryException.class,
                () -> beneficiaryService.initializeClaim(request, currentUserId));

        assertEquals(HttpStatus.GONE, ex.getStatus());
        verify(verificationRepository, never()).save(any());
    }

}