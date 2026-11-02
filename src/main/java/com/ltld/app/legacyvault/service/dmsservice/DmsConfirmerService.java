package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfirmationDecisionResponse;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfirmerRequest;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfirmerResponse;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.enums.DmsConfirmerDecision;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * X2: Dịch vụ quản lý người xác nhận chéo trước khi kích hoạt DMS (Trusted Social Confirmers).
 */
public interface DmsConfirmerService {

    /** Danh sách người xác nhận chéo của Owner (tối đa 2 người). */
    List<DmsConfirmerResponse> listConfirmers(UUID ownerId);

    /**
     * Thêm người xác nhận chéo: tính năng VIP (PLAN_LIMIT_REACHED với gói FREE), tối đa 2 người, không trùng email,
     * không phải chính Owner, không phải Executor còn hiệu lực của Owner.
     */
    DmsConfirmerResponse addConfirmer(UUID ownerId, DmsConfirmerRequest request);

    /** Xóa người xác nhận chéo của Owner. */
    void removeConfirmer(UUID ownerId, UUID confirmerId);

    /**
     * Sinh JWT xác nhận cho MỘT confirmer trong MỘT đợt (purpose=DMS_CONFIRM, jti dùng một lần).
     * Token không mang quyết định; claim round gắn token với hạn chờ của đợt (deadlineAt).
     */
    String generateConfirmationToken(UUID confirmerId, LocalDateTime deadlineAt);

    /**
     * Được gọi từ DmsActivationProcessor.trigger() khi DMS quá hạn, trong transaction của trigger:
     * - Owner có ít nhất 1 dms_confirmer -> chuyển PENDING_CONFIRMATION (72h), ghi audit và phát
     *   DmsConfirmationRequestedEvent; mail được gửi SAU commit (DmsConfirmationRequestListener). Trả về true.
     * - Chưa có confirmer nào -> trả về false để kích hoạt thẳng sang TRIGGERED.
     */
    boolean initiatePendingConfirmationIfConfigured(DmsConfig config, LocalDateTime now);

    /**
     * Dữ liệu để gửi mail cho đợt xác nhận (đọc trong transaction mới, sau commit của trigger).
     * Trả về null nếu đợt không còn hiệu lực (Owner đã check-in / lưu cấu hình, hay đợt khác đã bắt đầu).
     */
    ConfirmationMails prepareConfirmationMails(UUID configId, LocalDateTime deadlineAt);

    /**
     * Xử lý quyết định của Confirmer từ trang FE /dms/confirm (POST /api/dms/confirm):
     * - ALIVE: chỉ 1 lần mỗi đợt; DMS về ACTIVE, hạn mới +7 ngày, lịch nhắc dựng lại, báo Owner (sau commit).
     * - UNREACHABLE: lập tức chuyển DMS sang TRIGGERED và kích hoạt Executor.
     * Quyết định CHỈ lấy từ tham số (body request), không từ token. Token hết hạn / sai chữ ký / dùng lại /
     * thuộc đợt khác -> DmsException 400 CONFIRMER_TOKEN_INVALID.
     */
    DmsConfirmationDecisionResponse respondByToken(String rawToken, DmsConfirmerDecision decision, LocalDateTime now);

    /** Một mail xác nhận cho một confirmer. */
    record ConfirmerMail(String email, String name, String token) {
    }

    /** Mọi mail của một đợt xác nhận, kèm thông tin Owner để báo Owner (m5). */
    record ConfirmationMails(String ownerEmail, String ownerName, LocalDateTime deadlineAt,
                             List<ConfirmerMail> confirmers) {
    }
}
