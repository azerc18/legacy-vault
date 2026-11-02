package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.DmsCheckinResponse;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigRequest;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface DmsConfigService {

    /**
     * Cấu hình DMS của Owner (FR-03). Chưa có thì không lỗi: configured = false, hasUsableExecutor = có Executor
     * chính chưa REVOKED (điều kiện bật DMS lần đầu), các trường khác null.
     */
    DmsConfigResponse getConfig(UUID ownerId);

    /**
     * Lịch sử check-in của Owner (FR-03), mới nhất trước; thứ tự do hàm quyết định, sort trong pageable bị bỏ qua.
     * Chưa cấu hình -> trang rỗng.
     */
    Page<DmsCheckinResponse> getCheckins(UUID ownerId, Pageable pageable);

    /** Tạo mới hoặc cập nhật cấu hình DMS và bắt đầu chu kỳ mới tính từ lúc lưu (FR-03). */
    UpsertResult upsertConfig(UUID ownerId, DmsConfigRequest request);

    /** Owner xác nhận còn sống: bắt đầu chu kỳ mới, lùi hạn trigger (FR-03). */
    DmsConfigResponse checkIn(UUID ownerId);

    /**
     * Chặn thao tác của Owner khi DMS đã kích hoạt quy trình bàn giao (TRIGGERED) -> DmsException 409.
     * Dùng ở ĐẦU các thao tác của Owner bị cấm sau khi DMS kích hoạt (FR-02/FR-04/FR-27). Hàm khóa DmsConfig
     * (PESSIMISTIC_FORCE_INCREMENT) để không chạy đua với trigger FR-07. Nơi gọi PHẢI đang trong transaction
     * (MANDATORY), nếu không sẽ ném IllegalTransactionStateException. Chưa có DmsConfig hoặc trạng thái khác thì cho qua.
     */
    void assertHandoverNotStarted(UUID ownerId);

    /**
     * FR-27: kiểm tra đã bàn giao chưa (chỉ đọc, để hiển thị; KHÔNG dùng cho quyết định ghi).
     */
    boolean isHandoverStarted(UUID ownerId);

    /**
     * Q28/O5: Admin tạm dừng DMS của Owner (ACTIVE -> SUSPENDED), dùng cho A5. Nơi gọi PHẢI kiểm quyền ADMIN.
     * Khóa DmsConfig (PESSIMISTIC_FORCE_INCREMENT) để không chạy đua với trigger FR-07 / nhắc check-in / leo thang;
     * các job đó chỉ quét DMS ACTIVE nên bỏ qua Owner bị tạm dừng.
     * Chưa có DmsConfig -> DmsException 404; TRIGGERED hoặc đã SUSPENDED -> 409. reason được ghi vào audit.
     */
    DmsConfigResponse suspendByAdmin(UUID ownerId, UUID adminId, String reason);

    /**
     * Q28/O5: Admin mở lại DMS (SUSPENDED -> ACTIVE), dùng cho A5. Nơi gọi PHẢI kiểm quyền ADMIN.
     * Bắt đầu chu kỳ mới tính từ lúc mở lại (như check-in) để không bị kích hoạt ngay vì hạn cũ đã qua.
     * Khóa DmsConfig như suspendByAdmin. Chưa có DmsConfig -> 404; không phải SUSPENDED hoặc cấu hình
     * chưa đầy đủ -> 409.
     */
    DmsConfigResponse resumeByAdmin(UUID ownerId, UUID adminId);

    /** Kết quả upsert; created = true khi vừa tạo cấu hình lần đầu (controller trả 201). */
    record UpsertResult(DmsConfigResponse config, boolean created) {
    }
}
