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
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.enums.PlanFeature;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.PlanLimitReachedException;
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
    @DisplayName("Tạo lời nhắn gửi cả gia đình (ON_CLAIM) thành công")
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
    @DisplayName("Tạo lời nhắn gửi người thụ hưởng cụ thể + đính kèm media thành công")
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
    @DisplayName("Tạo lời nhắn hẹn ngày trong quá khứ ném lỗi 400 BAD_REQUEST")
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
    @DisplayName("Tệp đính kèm vượt quá giới hạn 10MB ném lỗi FILE_TOO_LARGE")
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
    @DisplayName("T1 (m7): đính kèm vượt quota gói bị chặn trước khi lưu file")
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
    @DisplayName("T1 (m7): thay đính kèm chỉ tính phần dung lượng tăng thêm vào quota")
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
        when(cryptoService.encrypt("New")).thenReturn("enc:New");
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

        // 25 byte mới - 10 byte cũ = 15 byte tăng thêm
        assertThatThrownBy(() -> messageService.updateMessage(ownerId, vaultId, messageId, request, file))
                .isInstanceOf(PlanLimitReachedException.class);
        verify(entitlementService).assertStorageQuota(ownerId, 15L);
        verify(fileStorageService, never()).storeFile(any(), any());
    }

    @Test
    @DisplayName("Người không sở hữu Vault gọi API ném lỗi 403 FORBIDDEN")
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
    @DisplayName("Xem danh sách lời nhắn trong Vault của Owner giải mã thành công")
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
    @DisplayName("Xóa lời nhắn thành công và dọn dẹp file vật lý")
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
    @DisplayName("Beneficiary xem danh sách: Két chưa UNLOCKED ném lỗi CLAIM_NOT_READY")
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
    @DisplayName("Beneficiary xem lời nhắn hẹn giờ tương lai: nội dung bị ẩn (Time-capsule teaser)")
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
    @DisplayName("F4b: Khi Vault mở khóa, phát các lời nhắn ON_CLAIM và gửi thông báo cho Beneficiary đủ điều kiện")
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

        int count = messageService.deliverOnClaimMessages(vaultId);

        assertThat(count).isEqualTo(1);
        assertThat(onClaimMsg.getStatus()).isEqualTo(LegacyMessageStatus.DELIVERED);
        assertThat(onClaimMsg.getDeliveredAt()).isNotNull();
        assertThat(futureMsg.getStatus()).isEqualTo(LegacyMessageStatus.ACTIVE);

        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(emailSender, times(1)).sendLegacyMessageDeliveredNotification(
                eq("child@test.com"), eq("Nguyen Van Con"), eq("Tran Chu So Huu"),
                eq("Family Vault"), eq("Farewell Letter"), eq(vaultId));
    }

    @Test
    @DisplayName("F4b: Chống gửi trùng - Nếu lời nhắn đã được gửi hoặc race condition (markDelivered=0) thì không gửi mail")
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
        when(legacyMessageRepository.markDelivered(eq(onClaimMsg.getId()), any(LocalDateTime.class)))
                .thenReturn(0);

        int count = messageService.deliverOnClaimMessages(vaultId);

        assertThat(count).isEqualTo(0);
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(emailSender, never()).sendLegacyMessageDeliveredNotification(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("F4b: Scheduler gửi lời nhắn hẹn giờ SPECIFIC_DATE đến hạn cho người nhận cụ thể")
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

        int delivered = messageService.deliverDueScheduledMessages(today);

        assertThat(delivered).isEqualTo(1);
        assertThat(dueCapsule.getStatus()).isEqualTo(LegacyMessageStatus.DELIVERED);
        assertThat(dueCapsule.getDeliveredAt()).isNotNull();
        verify(legacyMessageRepository).markDelivered(eq(dueCapsule.getId()), any(LocalDateTime.class));
        verify(notificationRepository).save(any(Notification.class));
        verify(emailSender).sendLegacyMessageDeliveredNotification(
                eq("child@test.com"), eq("Nguyen Van Con"), eq("Tran Chu So Huu"),
                eq("Family Vault"), eq("18th Birthday Letter"), eq(vaultId));
    }

    @Test
    @DisplayName("F4b: Beneficiary đã từ chối nhận di sản (DECLINED) không xem được lời nhắn")
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
    @DisplayName("F4b: Beneficiary cố đọc chi tiết Time-Capsule chưa đến ngày mở nhận lỗi 403 FORBIDDEN")
    void getMessageDetailForBeneficiary_FutureCapsule_ThrowsForbidden() {
        UUID userId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        vault.setStatus(VaultStatus.UNLOCKED);
        when(vaultBeneficiaryRepository.findByVaultIdAndUserId(vaultId, userId))
                .thenReturn(Optional.of(beneficiary));

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
}
