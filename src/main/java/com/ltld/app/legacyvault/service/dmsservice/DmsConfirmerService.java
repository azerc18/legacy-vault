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

    /** Thêm người xác nhận chéo (tối đa 2 người, không trùng email Owner). */
    DmsConfirmerResponse addConfirmer(UUID ownerId, DmsConfirmerRequest request);

    /** Xóa người xác nhận chéo của Owner. */
    void removeConfirmer(UUID ownerId, UUID confirmerId);

    /** Sinh JWT token xác nhận chéo cho một confirmer (purpose=DMS_CONFIRM, hạn 72h, jti one-time). */
    String generateConfirmationToken(UUID confirmerId, DmsConfirmerDecision decision);

    /**
     * Được gọi từ DmsActivationProcessor.trigger() khi DMS quá hạn:
     * - Nếu Owner có cấu hình ít nhất 1 dms_confirmer -> chuyển DMS sang PENDING_CONFIRMATION (72h),
     *   gửi email khẩn cấp kèm 2 link xác nhận cho các confirmer và trả về true.
     * - Nếu chưa có confirmer nào -> trả về false để kích hoạt thẳng sang TRIGGERED.
     */
    boolean initiatePendingConfirmationIfConfigured(DmsConfig config, LocalDateTime now);

    /**
     * Xử lý phản hồi của Confirmer từ link email:
     * - ALIVE ("Owner is safe"): gia hạn thời gian chờ thêm 7 ngày, chuyển DMS về ACTIVE, gửi cảnh báo cho Owner.
     * - UNREACHABLE ("Unreachable / Confirmed"): lập tức chuyển DMS sang TRIGGERED và kích hoạt Executor.
     * Token hết hạn / sai chữ ký / dùng lại lần 2 -> ném DmsException 400 CONFIRMER_TOKEN_INVALID.
     */
    DmsConfirmationDecisionResponse respondByToken(String rawToken, DmsConfirmerDecision decisionParam, LocalDateTime now);

    /** Xử lý phản hồi từ endpoint GET /api/dms/confirm và trả về URL chuyển hướng về FE. */
    String respondAndBuildRedirectUrl(String rawToken, String decisionParam);
}

