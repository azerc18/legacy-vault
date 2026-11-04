package com.ltld.app.legacyvault.service.messageservice;

import com.ltld.app.legacyvault.dto.messagedto.CreateLegacyMessageRequest;
import com.ltld.app.legacyvault.dto.messagedto.LegacyMessageResponse;
import com.ltld.app.legacyvault.dto.messagedto.UpdateLegacyMessageRequest;
import com.ltld.app.legacyvault.entity.LegacyMessage;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.enums.LegacyMessageDeliveryTrigger;
import com.ltld.app.legacyvault.enums.LegacyMessageRecipientType;
import com.ltld.app.legacyvault.enums.LegacyMessageStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.enums.PlanFeature;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.PlanLimitReachedException;
import com.ltld.app.legacyvault.repository.IdentityVerificationRepository;
import com.ltld.app.legacyvault.repository.LegacyMessageRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.paymentservice.EntitlementService;
import com.ltld.app.legacyvault.service.vaultservice.VaultModificationGuard;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegacyMessageServiceImplTest {

    @Mock
    private LegacyMessageRepository legacyMessageRepository;
    @Mock
    private VaultRepository vaultRepository;
    @Mock
    private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Mock
    private IdentityVerificationRepository identityVerificationRepository;
    @Mock
    private CryptoService cryptoService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private EntitlementService entitlementService;
    @Mock
    private VaultModificationGuard vaultModificationGuard;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private EmailSender emailSender;
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private LegacyMessageServiceImpl messageService;

    private UUID ownerId;
    private UUID vaultId;
    private UUID beneficiaryId;
    private User owner;
    private Vault vault;
    private VaultBeneficiary beneficiary;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        ownerId = UUID.randomUUID();
        vaultId = UUID.randomUUID();
        beneficiaryId = UUID.randomUUID();

        owner = User.builder().id(ownerId).email("owner@test.com").fullName("Tran Chu So Huu").build();
        vault = Vault.builder()
                .id(vaultId)
                .owner(owner)
                .status(VaultStatus.ACTIVE)
                .name("Family Vault")
                .build();

        beneficiary = VaultBeneficiary.builder()
                .id(beneficiaryId)
                .vault(vault)
                .email("child@test.com")
                .fullName("Nguyen Van Con")
                .status(BeneficiaryInviteStatus.INVITED)
                .build();
    }

    @Test
    @DisplayName("Tao loi nhan gui ca gia dinh (ON_CLAIM) thanh cong")
    void createMessage_AllBeneficiaries_Success() throws Exception {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        when(cryptoService.encrypt("My secret words")).thenReturn("enc:My secret words");

        LegacyMessage saved = LegacyMessage.builder()
                .id(UUID.randomUUID())
                .vault(vault)
                .recipientType(LegacyMessageRecipientType.ALL_BENEFICIARIES)
                .title("Letter to Family")
                .content("enc:My secret words")
                .deliveryTrigger(LegacyMessageDeliveryTrigger.ON_CLAIM)
                .status(LegacyMessageStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(legacyMessageRepository.save(any(LegacyMessage.class))).thenReturn(saved);

        CreateLegacyMessageRequest request = CreateLegacyMessageRequest.builder()
                .title("Letter to Family")
                .content("My secret words")
                .recipientType(LegacyMessageRecipientType.ALL_BENEFICIARIES)
                .deliveryTrigger(LegacyMessageDeliveryTrigger.ON_CLAIM)
                .build();

        LegacyMessageResponse response = messageService.createMessage(ownerId, vaultId, request, null);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Letter to Family");
        assertThat(response.getContent()).isEqualTo("My secret words");
        assertThat(response.getRecipientType()).isEqualTo(LegacyMessageRecipientType.ALL_BENEFICIARIES);
        assertThat(response.isHasAttachment()).isFalse();

        verify(vaultModificationGuard).ensureModifiable(vault);
        verify(entitlementService).assertCanUseLegacyMessages(ownerId);
        verify(legacyMessageRepository).save(any(LegacyMessage.class));
    }

    @Test
    @DisplayName("Tao loi nhan gui nguoi thu huong cu the + dinh kem media thanh cong")
    void createMessage_SpecificBeneficiary_WithAttachment_Success() throws Exception {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        when(vaultBeneficiaryRepository.findById(beneficiaryId)).thenReturn(Optional.of(beneficiary));
        when(cryptoService.encrypt("Happy 18th Birthday")).thenReturn("enc:Happy 18th Birthday");
        when(cryptoService.encryptBytes(any())).thenReturn("encBytes".getBytes());
        when(fileStorageService.storeFile(any(), any())).thenReturn("uploads/messages/video.mp4");

        MockMultipartFile file = new MockMultipartFile(
                "attachment", "video.mp4", "video/mp4", "fake video data".getBytes());

        LocalDate birthday = LocalDate.now().plusYears(1);

        LegacyMessage saved = LegacyMessage.builder()
                .id(UUID.randomUUID())
                .vault(vault)
                .recipientType(LegacyMessageRecipientType.SPECIFIC_BENEFICIARY)
                .recipientBeneficiary(beneficiary)
                .title("Birthday Video")
                .content("enc:Happy 18th Birthday")
                .deliveryTrigger(LegacyMessageDeliveryTrigger.SPECIFIC_DATE)
                .deliverDate(birthday)
                .status(LegacyMessageStatus.ACTIVE)
                .attachmentName("video.mp4")
                .attachmentPath("uploads/messages/video.mp4")
                .attachmentType("video/mp4")
                .attachmentSize(file.getSize())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(legacyMessageRepository.save(any(LegacyMessage.class))).thenReturn(saved);

        CreateLegacyMessageRequest request = CreateLegacyMessageRequest.builder()
                .title("Birthday Video")
                .content("Happy 18th Birthday")
                .recipientType(LegacyMessageRecipientType.SPECIFIC_BENEFICIARY)
                .recipientBeneficiaryId(beneficiaryId)
                .deliveryTrigger(LegacyMessageDeliveryTrigger.SPECIFIC_DATE)
                .deliverDate(birthday)
                .build();

        LegacyMessageResponse response = messageService.createMessage(ownerId, vaultId, request, file);

        assertThat(response).isNotNull();
        assertThat(response.isHasAttachment()).isTrue();
        assertThat(response.getAttachmentName()).isEqualTo("video.mp4");
        assertThat(response.getRecipientName()).isEqualTo("Nguyen Van Con");
    }

    @Test
    @DisplayName("Tao loi nhan hen ngay trong qua khu nem loi 400 BAD_REQUEST")
    void createMessage_DeliverDateInPast_Throws() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));

        CreateLegacyMessageRequest request = CreateLegacyMessageRequest.builder()
                .title("Invalid Date")
                .content("Past")
                .recipientType(LegacyMessageRecipientType.ALL_BENEFICIARIES)
                .deliveryTrigger(LegacyMessageDeliveryTrigger.SPECIFIC_DATE)
                .deliverDate(LocalDate.now().minusDays(1))
                .build();

        assertThatThrownBy(() -> messageService.createMessage(ownerId, vaultId, request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("deliverDate cannot be in the past");
    }

    @Test
    @DisplayName("Tep dinh kem vuot qua gioi han 10MB nem loi FILE_TOO_LARGE")
    void createMessage_AttachmentExceeds10MB_Throws() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));

        MockMultipartFile largeFile = new MockMultipartFile(
                "attachment", "large.mp4", "video/mp4", new byte[10 * 1024 * 1024 + 1]);

        CreateLegacyMessageRequest request = CreateLegacyMessageRequest.builder()
                .title("Too large")
                .content("Test")
                .recipientType(LegacyMessageRecipientType.ALL_BENEFICIARIES)
                .deliveryTrigger(LegacyMessageDeliveryTrigger.ON_CLAIM)
                .build();

        assertThatThrownBy(() -> messageService.createMessage(ownerId, vaultId, request, largeFile))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Attachment size exceeds 10MB limit");
    }

    @Test
    @DisplayName("T1 (m7): dinh kem vuot quota goi bi chan truoc khi luu file")
    void createMessage_AttachmentOverStorageQuota_Throws() throws Exception {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        MockMultipartFile file = new MockMultipartFile(
                "attachment", "video.mp4", "video/mp4", "fake video data".getBytes());
        doThrow(new PlanLimitReachedException(PlanFeature.STORAGE, "Storage quota exceeded"))
                .when(entitlementService).assertStorageQuota(ownerId, file.getSize());

        CreateLegacyMessageRequest request = CreateLegacyMessageRequest.builder()
                .title("Video")
                .content("Hello")
                .recipientType(LegacyMessageRecipientType.ALL_BENEFICIARIES)
                .deliveryTrigger(LegacyMessageDeliveryTrigger.ON_CLAIM)
                .build();

        assertThatThrownBy(() -> messageService.createMessage(ownerId, vaultId, request, file))
                .isInstanceOf(PlanLimitReachedException.class);
        verify(fileStorageService, never()).storeFile(any(), any());
        verify(legacyMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("T1 (m7): thay dinh kem chi tinh phan dung luong tang them vao quota")
    void updateMessage_ReplaceAttachment_ChecksOnlyGrowth() throws Exception {
        UUID messageId = UUID.randomUUID();
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        LegacyMessage existing = LegacyMessage.builder()
                .id(messageId)
                .vault(vault)
                .title("Old")
                .content("enc:old")
                .attachmentName("old.mp4")
                .attachmentPath("uploads/messages/old.mp4")
                .attachmentType("video/mp4")
                .attachmentSize(10L)
                .build();
        when(legacyMessageRepository.findByIdAndVaultId(messageId, vaultId)).thenReturn(Optional.of(existing));
        MockMultipartFile file = new MockMultipartFile(
                "attachment", "new.mp4", "video/mp4", new byte[25]);
        doThrow(new PlanLimitReachedException(PlanFeature.STORAGE, "Storage quota exceeded"))
                .when(entitlementService).assertStorageQuota(ownerId, 15L);

        UpdateLegacyMessageRequest request = UpdateLegacyMessageRequest.builder()
                .title("New")
                .content("New")
                .recipientType(LegacyMessageRecipientType.ALL_BENEFICIARIES)
                .deliveryTrigger(LegacyMessageDeliveryTrigger.ON_CLAIM)
                .build();

        // 25 byte moi - 10 byte cu = 15 byte tang them
        assertThatThrownBy(() -> messageService.updateMessage(ownerId, vaultId, messageId, request, file))
                .isInstanceOf(PlanLimitReachedException.class);
        verify(entitlementService).assertStorageQuota(ownerId, 15L);
        verify(fileStorageService, never()).storeFile(any(), any());
    }

    @Test
    @DisplayName("Nguoi khong so huu Vault goi API nem loi 403 FORBIDDEN")
    void createMessage_NotVaultOwner_Throws() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));

        UUID strangerId = UUID.randomUUID();
        CreateLegacyMessageRequest request = CreateLegacyMessageRequest.builder()
                .title("Hack")
                .content("Content")
                .recipientType(LegacyMessageRecipientType.ALL_BENEFICIARIES)
                .deliveryTrigger(LegacyMessageDeliveryTrigger.ON_CLAIM)
                .build();

        assertThatThrownBy(() -> messageService.createMessage(strangerId, vaultId, request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("You do not own this vault");
    }

    @Test
    @DisplayName("Xem danh sach loi nhan trong Vault cua Owner giai ma thanh cong")
    void getMessagesByVault_Success() throws Exception {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));

        LegacyMessage m = LegacyMessage.builder()
                .id(UUID.randomUUID())
                .vault(vault)
                .recipientType(LegacyMessageRecipientType.ALL_BENEFICIARIES)
                .title("Letter 1")
                .content("enc:Hello")
                .deliveryTrigger(LegacyMessageDeliveryTrigger.ON_CLAIM)
                .status(LegacyMessageStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(legacyMessageRepository.findByVaultIdOrderByCreatedAtDesc(vaultId)).thenReturn(List.of(m));
        when(cryptoService.decrypt("enc:Hello")).thenReturn("Hello");

        List<LegacyMessageResponse> list = messageService.getMessagesByVault(ownerId, vaultId);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getContent()).isEqualTo("Hello");
    }

    @Test
    @DisplayName("Xoa loi nhan thanh cong va don dep file vat ly")
    void deleteMessage_Success() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));

        UUID msgId = UUID.randomUUID();
        LegacyMessage m = LegacyMessage.builder()
                .id(msgId)
                .vault(vault)
                .title("To Delete")
                .attachmentPath("uploads/msg.pdf")
                .build();

        when(legacyMessageRepository.findByIdAndVaultId(msgId, vaultId)).thenReturn(Optional.of(m));

        messageService.deleteMessage(ownerId, vaultId, msgId);

        verify(legacyMessageRepository).delete(m);
        verify(fileStorageService).deleteFile("uploads/msg.pdf");
    }

    @Test
    @DisplayName("Beneficiary xem danh sach: Ket chua UNLOCKED nem loi CLAIM_NOT_READY")
    void getMessagesForBeneficiary_VaultActive_Throws() {
        UUID userId = UUID.randomUUID();
        when(vaultBeneficiaryRepository.findByVaultIdAndUserId(vaultId, userId))
                .thenReturn(Optional.of(beneficiary));

        // vault.status is ACTIVE, not UNLOCKED
        assertThatThrownBy(() -> messageService.getMessagesForBeneficiary(userId, vaultId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Vault is not unlocked");
    }

    @Test
    @DisplayName("m2: Nguoi khong phai beneficiary cua vault goi API nhan loi 404 NOT_FOUND")
    void getMessagesForBeneficiary_NotBeneficiary_ThrowsNotFound() {
        UUID userId = UUID.randomUUID();
        when(vaultBeneficiaryRepository.findByVaultIdAndUserId(vaultId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.getMessagesForBeneficiary(userId, vaultId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("M2: Beneficiary chua co phien xac thuc danh tinh (view session) thi noi dung bi an (null)")
    void getMessagesForBeneficiary_WithoutViewSession_HidesContent() {
        UUID userId = UUID.randomUUID();
        vault.setStatus(VaultStatus.UNLOCKED);
        when(vaultBeneficiaryRepository.findByVaultIdAndUserId(vaultId, userId))
                .thenReturn(Optional.of(beneficiary));
        when(identityVerificationRepository.existsByVaultIdAndBeneficiaryIdAndStatusAndVerifiedAtAfter(
                eq(vaultId), eq(userId), eq(VerificationStatus.SUCCESS), any()))
                .thenReturn(false);

        LegacyMessage unlockedMsg = LegacyMessage.builder()
                .id(UUID.randomUUID())
                .vault(vault)
                .recipientType(LegacyMessageRecipientType.ALL_BENEFICIARIES)
                .title("Unlocked letter")
                .content("enc:Secret")
                .deliveryTrigger(LegacyMessageDeliveryTrigger.ON_CLAIM)
                .status(LegacyMessageStatus.ACTIVE)
                .build();

        when(legacyMessageRepository.findForBeneficiary(eq(vaultId), any(), any()))
                .thenReturn(List.of(unlockedMsg));

        List<LegacyMessageResponse> result = messageService.getMessagesForBeneficiary(userId, vaultId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Unlocked letter");
        assertThat(result.get(0).isUnlocked()).isTrue();
        assertThat(result.get(0).getContent()).isNull(); // Content an khi chua co view session!
    }

    @Test
    @DisplayName("M2: Beneficiary da co phien xac thuc danh tinh (view session) thi giai ma noi dung loi nhan mo khoa")
    void getMessagesForBeneficiary_WithViewSession_ShowsContent() throws Exception {
        UUID userId = UUID.randomUUID();
        vault.setStatus(VaultStatus.UNLOCKED);
        when(vaultBeneficiaryRepository.findByVaultIdAndUserId(vaultId, userId))
                .thenReturn(Optional.of(beneficiary));
        when(identityVerificationRepository.existsByVaultIdAndBeneficiaryIdAndStatusAndVerifiedAtAfter(
                eq(vaultId), eq(userId), eq(VerificationStatus.SUCCESS), any()))
                .thenReturn(true);
        when(cryptoService.decrypt("enc:Secret")).thenReturn("Decrypted Secret");

        LegacyMessage unlockedMsg = LegacyMessage.builder()
                .id(UUID.randomUUID())
                .vault(vault)
                .recipientType(LegacyMessageRecipientType.ALL_BENEFICIARIES)
                .title("Unlocked letter")
                .content("enc:Secret")
                .deliveryTrigger(LegacyMessageDeliveryTrigger.ON_CLAIM)
                .status(LegacyMessageStatus.ACTIVE)
                .build();

        when(legacyMessageRepository.findForBeneficiary(eq(vaultId), any(), any()))
                .thenReturn(List.of(unlockedMsg));

        List<LegacyMessageResponse> result = messageService.getMessagesForBeneficiary(userId, vaultId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("Decrypted Secret");
    }

    @Test
    @DisplayName("Beneficiary xem loi nhan hen gio tuong lai: noi dung bi an (Time-capsule teaser)")
    void getMessagesForBeneficiary_FutureDate_TeaserUnlockedFalse() {
        UUID userId = UUID.randomUUID();
        vault.setStatus(VaultStatus.UNLOCKED);
        when(vaultBeneficiaryRepository.findByVaultIdAndUserId(vaultId, userId))
                .thenReturn(Optional.of(beneficiary));

        LocalDate future = LocalDate.now().plusMonths(6);
        LegacyMessage timeCapsule = LegacyMessage.builder()
                .id(UUID.randomUUID())
                .vault(vault)
                .recipientType(LegacyMessageRecipientType.ALL_BENEFICIARIES)
                .title("Open in 2027")
                .content("enc:Hidden surprise")
                .deliveryTrigger(LegacyMessageDeliveryTrigger.SPECIFIC_DATE)
                .deliverDate(future)
                .status(LegacyMessageStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(legacyMessageRepository.findForBeneficiary(eq(vaultId), any(), any()))
                .thenReturn(List.of(timeCapsule));

        List<LegacyMessageResponse> result = messageService.getMessagesForBeneficiary(userId, vaultId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Open in 2027");
        assertThat(result.get(0).isUnlocked()).isFalse();
        assertThat(result.get(0).getContent()).isNull(); // Content hidden!
    }

    @Test
    @DisplayName("F4b & M1: Khi Vault mo khoa, phat cac loi nhan ON_CLAIM va gui thong bao cho Beneficiary du dieu kien")
    void deliverOnClaimMessages_UnlockedVault_DeliversOnClaimAndNotifiesBeneficiaries() {
        vault.setStatus(VaultStatus.UNLOCKED);
        User beneficiaryUser = User.builder().id(UUID.randomUUID()).email("child@test.com").fullName("Nguyen Van Con").build();
        beneficiary.setUser(beneficiaryUser);

        VaultBeneficiary declinedBeneficiary = VaultBeneficiary.builder()
                .id(UUID.randomUUID())
                .vault(vault)
                .email("declined@test.com")
                .status(BeneficiaryInviteStatus.DECLINED)
                .build();

        LegacyMessage onClaimMsg = LegacyMessage.builder()
                .id(UUID.randomUUID())
                .vault(vault)
                .recipientType(LegacyMessageRecipientType.ALL_BENEFICIARIES)
                .title("Farewell Letter")
                .content("enc:Goodbye")
                .deliveryTrigger(LegacyMessageDeliveryTrigger.ON_CLAIM)
                .status(LegacyMessageStatus.ACTIVE)
                .build();

        LegacyMessage futureMsg = LegacyMessage.builder()
                .id(UUID.randomUUID())
                .vault(vault)
                .recipientType(LegacyMessageRecipientType.ALL_BENEFICIARIES)
                .title("Future Capsule")
                .content("enc:Wait")
                .deliveryTrigger(LegacyMessageDeliveryTrigger.SPECIFIC_DATE)
                .deliverDate(LocalDate.now().plusDays(30))
                .status(LegacyMessageStatus.ACTIVE)
                .build();

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        when(legacyMessageRepository.findByVaultIdAndStatus(vaultId, LegacyMessageStatus.ACTIVE))
                .thenReturn(List.of(onClaimMsg, futureMsg));
        when(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId))
                .thenReturn(List.of(beneficiary, declinedBeneficiary));
        when(legacyMessageRepository.markDelivered(eq(onClaimMsg.getId()), any(LocalDateTime.class)))
                .thenReturn(1);
        when(legacyMessageRepository.findById(onClaimMsg.getId()))
                .thenReturn(Optional.of(onClaimMsg));

        int count = messageService.deliverOnClaimMessages(vaultId);

        assertThat(count).isEqualTo(1);
        assertThat(onClaimMsg.getStatus()).isEqualTo(LegacyMessageStatus.DELIVERED);
        assertThat(onClaimMsg.getDeliveredAt()).isNotNull();
        assertThat(futureMsg.getStatus()).isEqualTo(LegacyMessageStatus.ACTIVE);

        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(emailSender, times(1)).sendLegacyMessageDeliveredNotification(
                eq("child@test.com"), eq("Nguyen Van Con"), eq("Tran Chu So Huu"),
                eq("Family Vault"), eq(vaultId), eq(true));
    }

    @Test
    @DisplayName("F4b: Chong gui trung - Neu loi nhan da duoc gui hoac race condition (markDelivered=0) thi khong gui mail")
    void deliverOnClaimMessages_ConcurrentOrSecondCall_DoesNotDuplicate() {
        vault.setStatus(VaultStatus.UNLOCKED);
        User beneficiaryUser = User.builder().id(UUID.randomUUID()).email("child@test.com").fullName("Nguyen Van Con").build();
        beneficiary.setUser(beneficiaryUser);

        LegacyMessage onClaimMsg = LegacyMessage.builder()
                .id(UUID.randomUUID())
                .vault(vault)
                .recipientType(LegacyMessageRecipientType.ALL_BENEFICIARIES)
                .title("Farewell Letter")
                .content("enc:Goodbye")
                .deliveryTrigger(LegacyMessageDeliveryTrigger.ON_CLAIM)
                .status(LegacyMessageStatus.ACTIVE)
                .build();

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        when(legacyMessageRepository.findByVaultIdAndStatus(vaultId, LegacyMessageStatus.ACTIVE))
                .thenReturn(List.of(onClaimMsg));
        when(legacyMessageRepository.findById(onClaimMsg.getId()))
                .thenReturn(Optional.of(onClaimMsg));
        when(legacyMessageRepository.markDelivered(eq(onClaimMsg.getId()), any(LocalDateTime.class)))
                .thenReturn(0);

        int count = messageService.deliverOnClaimMessages(vaultId);

        assertThat(count).isEqualTo(0);
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(emailSender, never()).sendLegacyMessageDeliveredNotification(
                any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("F4b & M1: Scheduler gui loi nhan hen gio SPECIFIC_DATE den han cho nguoi nhan cu the")
    void deliverDueScheduledMessages_DueDate_DeliversAndNotifiesSpecificRecipient() {
        vault.setStatus(VaultStatus.CLAIMED);
        User beneficiaryUser = User.builder().id(UUID.randomUUID()).email("child@test.com").fullName("Nguyen Van Con").build();
        beneficiary.setUser(beneficiaryUser);

        LocalDate today = LocalDate.now();
        LegacyMessage dueCapsule = LegacyMessage.builder()
                .id(UUID.randomUUID())
                .vault(vault)
                .recipientType(LegacyMessageRecipientType.SPECIFIC_BENEFICIARY)
                .recipientBeneficiary(beneficiary)
                .title("18th Birthday Letter")
                .content("enc:Happy Birthday")
                .deliveryTrigger(LegacyMessageDeliveryTrigger.SPECIFIC_DATE)
                .deliverDate(today)
                .status(LegacyMessageStatus.ACTIVE)
                .build();

        when(legacyMessageRepository.findDueMessagesForUnlockedVaults(
                eq(LegacyMessageStatus.ACTIVE), any(),
                eq(LegacyMessageDeliveryTrigger.ON_CLAIM),
                eq(LegacyMessageDeliveryTrigger.SPECIFIC_DATE),
                eq(today)))
                .thenReturn(List.of(dueCapsule));
        when(legacyMessageRepository.markDelivered(eq(dueCapsule.getId()), any(LocalDateTime.class)))
                .thenReturn(1);
        when(legacyMessageRepository.findById(dueCapsule.getId()))
                .thenReturn(Optional.of(dueCapsule));

        int delivered = messageService.deliverDueScheduledMessages(today);

        assertThat(delivered).isEqualTo(1);
        assertThat(dueCapsule.getStatus()).isEqualTo(LegacyMessageStatus.DELIVERED);
        assertThat(dueCapsule.getDeliveredAt()).isNotNull();
        verify(legacyMessageRepository).markDelivered(eq(dueCapsule.getId()), any(LocalDateTime.class));
        verify(notificationRepository).save(any(Notification.class));
        verify(emailSender).sendLegacyMessageDeliveredNotification(
                eq("child@test.com"), eq("Nguyen Van Con"), eq("Tran Chu So Huu"),
                eq("Family Vault"), eq(vaultId), eq(true));
    }

    @Test
    @DisplayName("F4b: Beneficiary da tu choi nhan di san (DECLINED) khong xem duoc loi nhan")
    void getMessagesForBeneficiary_DeclinedBeneficiary_Throws() {
        UUID userId = UUID.randomUUID();
        vault.setStatus(VaultStatus.UNLOCKED);
        beneficiary.setStatus(BeneficiaryInviteStatus.DECLINED);
        when(vaultBeneficiaryRepository.findByVaultIdAndUserId(vaultId, userId))
                .thenReturn(Optional.of(beneficiary));

        assertThatThrownBy(() -> messageService.getMessagesForBeneficiary(userId, vaultId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("You have declined this inheritance");
    }

    @Test
    @DisplayName("M2: Beneficiary chua co phien xac thuc danh tinh co doc chi tiet loi nhan bi nem loi IDENTITY_NOT_VERIFIED")
    void getMessageDetailForBeneficiary_WithoutViewSession_ThrowsIdentityNotVerified() {
        UUID userId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        when(vaultBeneficiaryRepository.findByVaultIdAndUserId(vaultId, userId))
                .thenReturn(Optional.of(beneficiary));
        when(identityVerificationRepository.existsByVaultIdAndBeneficiaryIdAndStatusAndVerifiedAtAfter(
                eq(vaultId), eq(userId), eq(VerificationStatus.SUCCESS), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> messageService.getMessageDetailForBeneficiary(userId, vaultId, msgId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IDENTITY_NOT_VERIFIED);
    }

    @Test
    @DisplayName("m2: Loi nhan danh rieng cho beneficiary khac (SPECIFIC_BENEFICIARY) tra ve 404 NOT_FOUND")
    void getMessageDetailForBeneficiary_NotAddressedToUser_ThrowsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        vault.setStatus(VaultStatus.UNLOCKED);
        when(vaultBeneficiaryRepository.findByVaultIdAndUserId(vaultId, userId))
                .thenReturn(Optional.of(beneficiary));
        when(identityVerificationRepository.existsByVaultIdAndBeneficiaryIdAndStatusAndVerifiedAtAfter(
                eq(vaultId), eq(userId), eq(VerificationStatus.SUCCESS), any()))
                .thenReturn(true);

        VaultBeneficiary otherBeneficiary = VaultBeneficiary.builder()
                .id(UUID.randomUUID())
                .vault(vault)
                .email("other@test.com")
                .build();

        LegacyMessage specificMsg = LegacyMessage.builder()
                .id(msgId)
                .vault(vault)
                .recipientType(LegacyMessageRecipientType.SPECIFIC_BENEFICIARY)
                .recipientBeneficiary(otherBeneficiary)
                .title("Private message")
                .content("enc:Secret")
                .deliveryTrigger(LegacyMessageDeliveryTrigger.ON_CLAIM)
                .status(LegacyMessageStatus.ACTIVE)
                .build();

        when(legacyMessageRepository.findByIdAndVaultId(msgId, vaultId))
                .thenReturn(Optional.of(specificMsg));

        assertThatThrownBy(() -> messageService.getMessageDetailForBeneficiary(userId, vaultId, msgId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("F4b: Beneficiary co doc chi tiet Time-Capsule chua den ngay mo nhan loi 403 FORBIDDEN")
    void getMessageDetailForBeneficiary_FutureCapsule_ThrowsForbidden() {
        UUID userId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        vault.setStatus(VaultStatus.UNLOCKED);
        when(vaultBeneficiaryRepository.findByVaultIdAndUserId(vaultId, userId))
                .thenReturn(Optional.of(beneficiary));
        when(identityVerificationRepository.existsByVaultIdAndBeneficiaryIdAndStatusAndVerifiedAtAfter(
                eq(vaultId), eq(userId), eq(VerificationStatus.SUCCESS), any()))
                .thenReturn(true);

        LegacyMessage futureCapsule = LegacyMessage.builder()
                .id(msgId)
                .vault(vault)
                .recipientType(LegacyMessageRecipientType.ALL_BENEFICIARIES)
                .title("Locked Capsule")
                .content("enc:Secret")
                .deliveryTrigger(LegacyMessageDeliveryTrigger.SPECIFIC_DATE)
                .deliverDate(LocalDate.now().plusDays(10))
                .status(LegacyMessageStatus.ACTIVE)
                .build();

        when(legacyMessageRepository.findByIdAndVaultId(msgId, vaultId))
                .thenReturn(Optional.of(futureCapsule));

        assertThatThrownBy(() -> messageService.getMessageDetailForBeneficiary(userId, vaultId, msgId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("This time-capsule message is scheduled for");
    }

    @Test
    @DisplayName("m2 (vong 3): Khi Vault da CLAIMED (dong ho so), beneficiary van doc duoc loi nhan da mo khoa du khong co view session 30 phut")
    void getMessageDetailForBeneficiary_ClaimedVaultWithoutViewSession_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        vault.setStatus(VaultStatus.CLAIMED);
        when(vaultBeneficiaryRepository.findByVaultIdAndUserId(vaultId, userId))
                .thenReturn(Optional.of(beneficiary));

        LegacyMessage deliveredMsg = LegacyMessage.builder()
                .id(msgId)
                .vault(vault)
                .recipientType(LegacyMessageRecipientType.ALL_BENEFICIARIES)
                .title("Time-Capsule Delivered After Claim")
                .content("enc:Future Message")
                .deliveryTrigger(LegacyMessageDeliveryTrigger.SPECIFIC_DATE)
                .deliverDate(LocalDate.now())
                .status(LegacyMessageStatus.DELIVERED)
                .build();

        when(legacyMessageRepository.findByIdAndVaultId(msgId, vaultId))
                .thenReturn(Optional.of(deliveredMsg));
        when(cryptoService.decrypt("enc:Future Message")).thenReturn("Future Message");

        LegacyMessageResponse response = messageService.getMessageDetailForBeneficiary(userId, vaultId, msgId);

        assertThat(response.getTitle()).isEqualTo("Time-Capsule Delivered After Claim");
        assertThat(response.getContent()).isEqualTo("Future Message");
        assertThat(response.isUnlocked()).isTrue();
    }
}
