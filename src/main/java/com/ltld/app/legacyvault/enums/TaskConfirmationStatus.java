package com.ltld.app.legacyvault.enums;

/** C.6: Beneficiary xác nhận chéo kết quả Executor báo DONE. null = task chưa DONE (hoặc DONE từ trước PR 2). */
public enum TaskConfirmationStatus {
    PENDING,    // Executor đã báo DONE, đang trong thời hạn để Beneficiary xác nhận / phản đối
    CONFIRMED,  // Beneficiary đã xác nhận
    DISPUTED    // Beneficiary phản đối, task quay về DOING
}
