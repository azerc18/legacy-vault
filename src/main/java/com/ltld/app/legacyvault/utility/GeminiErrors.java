package com.ltld.app.legacyvault.utility;

import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Phân loại lỗi khi gọi Gemini, dùng chung cho Vaulty (GeminiAiClient) và OCR giấy chứng tử.
 */
public final class GeminiErrors {

    private static final int MAX_CAUSE_DEPTH = 10;

    private GeminiErrors() {
    }

    /**
     * Chỉ chuyển sang model dự phòng khi đổi model hoặc gọi lại có thể hết lỗi:
     * <ul>
     *   <li>404: model chính bị Google ngừng hoặc đổi tên ("models/... is not found"), model dự phòng vẫn dùng được;</li>
     *   <li>429: hết hạn mức / bị giới hạn tốc độ;</li>
     *   <li>5xx: lỗi phía Google;</li>
     *   <li>lỗi mạng hoặc timeout.</li>
     * </ul>
     * Không chuyển với 400 (sai API key, file lỗi, nội dung bị chặn) hay 401/403: model dự phòng cũng sẽ lỗi y hệt.
     */
    public static boolean isRetryable(Throwable ex) {
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current instanceof HttpStatusCodeException hsce) {
                int status = hsce.getStatusCode().value();
                return status == 404 || status == 429 || hsce.getStatusCode().is5xxServerError();
            }
            if (current instanceof ResourceAccessException
                    || current instanceof TimeoutException
                    || current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }
}
