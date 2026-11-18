package com.ltld.app.legacyvault.service.aifallbackservice;

/**
 * Nhà cung cấp AI dự phòng trả lỗi HTTP. Message chỉ gồm tên nhà cung cấp, mã HTTP và thông báo lỗi của họ
 * (vd. "model ... does not exist"), không bao giờ có nội dung tài liệu, nên ghi log được.
 */
public class AiProviderException extends RuntimeException {

    private final int status;

    public AiProviderException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
