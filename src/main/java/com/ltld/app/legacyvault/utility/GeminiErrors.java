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
    /**
     * Mô tả lỗi để ghi log: chuỗi tên lớp exception (ngoài -> gốc), mã HTTP, và thông báo của lỗi mạng / nhà cung cấp.
     * KHÔNG lấy message của lỗi parse JSON vì Jackson chèn đoạn nội dung trả về (có thể chứa số CCCD, họ tên).
     * Ví dụ: "RestClientException <- SocketTimeoutException(Read timed out)", "HttpClientErrorException(404)".
     */
    public static String describe(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (sb.length() > 0) sb.append(" <- ");
            sb.append(current.getClass().getSimpleName());
            String detail = safeDetail(current);
            if (detail != null) sb.append('(').append(detail).append(')');
            current = current.getCause() == current ? null : current.getCause();
            depth++;
        }
        return sb.toString();
    }

    private static String safeDetail(Throwable t) {
        if (t instanceof HttpStatusCodeException h) {
            return String.valueOf(h.getStatusCode().value());
        }
        if (t instanceof java.io.IOException && t.getClass().getName().startsWith("java.net")
                || t instanceof TimeoutException
                || t.getClass().getName().equals("com.ltld.app.legacyvault.service.aifallbackservice.AiProviderException")) {
            String m = t.getMessage();
            return m == null ? null : (m.length() > 300 ? m.substring(0, 300) + "..." : m);
        }
        return null;
    }

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
