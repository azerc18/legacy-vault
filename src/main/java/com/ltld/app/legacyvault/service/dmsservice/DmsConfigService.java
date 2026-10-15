package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigRequest;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;

import java.util.UUID;

public interface DmsConfigService {

    /** Cấu hình DMS của Owner (FR-03); chưa có thì 404. */
    DmsConfigResponse getConfig(UUID ownerId);

    /** Tạo mới hoặc cập nhật cấu hình DMS và bắt đầu chu kỳ mới tính từ lúc lưu (FR-03). */
    UpsertResult upsertConfig(UUID ownerId, DmsConfigRequest request);

    /** Owner xác nhận còn sống: bắt đầu chu kỳ mới, lùi hạn trigger (FR-03). */
    DmsConfigResponse checkIn(UUID ownerId);

    /** Kết quả upsert; created = true khi vừa tạo cấu hình lần đầu (controller trả 201). */
    record UpsertResult(DmsConfigResponse config, boolean created) {
    }
}
