package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;

/**
 * Kiểm ảnh chữ ký tay Verifier gửi lên (PNG Base64 từ canvas).
 * <ul>
 *   <li>Chỉ nhận PNG (kiểm 8 byte đầu, không tin tiền tố data URL), tối đa {@value #MAX_BYTES} byte.</li>
 *   <li>Kích thước ảnh trong khoảng hợp lý để in lên hồ sơ thừa kế.</li>
 *   <li>Phải có đủ nét mực ({@value #MIN_INK_PIXELS} điểm ảnh tối, không trong suốt), để không ký bằng khung trống
 *       hoặc một chấm.</li>
 * </ul>
 * Không kiểm "giống chữ ký thật": hình vẽ không chứng minh danh tính, chỉ là hình thức thể hiện ý chí ký.
 */
public final class HandwrittenSignatureImage {

    static final int MAX_BYTES = 200 * 1024;
    static final int MIN_WIDTH = 200;
    static final int MIN_HEIGHT = 80;
    static final int MAX_SIDE = 2000;
    static final int MIN_INK_PIXELS = 150;
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final String DATA_URL_PREFIX = "data:image/png;base64,";

    private HandwrittenSignatureImage() {
    }

    /** Trả byte PNG đã kiểm; sai thì ném {@link ErrorCode#INVALID_SIGNATURE_IMAGE} (400). */
    public static byte[] decodeAndValidate(String input) {
        if (input == null || input.isBlank()) {
            throw invalid("Please draw your signature.");
        }
        String b64 = input.strip();
        if (b64.regionMatches(true, 0, DATA_URL_PREFIX, 0, DATA_URL_PREFIX.length())) {
            b64 = b64.substring(DATA_URL_PREFIX.length());
        }
        byte[] png;
        try {
            png = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw invalid("The signature image is not valid Base64.");
        }
        if (png.length > MAX_BYTES) {
            throw invalid("The signature image is too large (max 200 KB).");
        }
        if (png.length < PNG_MAGIC.length || !Arrays.equals(Arrays.copyOf(png, PNG_MAGIC.length), PNG_MAGIC)) {
            throw invalid("The signature must be a PNG image.");
        }
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException | RuntimeException e) {
            image = null;
        }
        if (image == null) {
            throw invalid("The signature image could not be read.");
        }
        int w = image.getWidth();
        int h = image.getHeight();
        if (w < MIN_WIDTH || h < MIN_HEIGHT || w > MAX_SIDE || h > MAX_SIDE) {
            throw invalid("The signature image has an unexpected size.");
        }
        if (countInk(image) < MIN_INK_PIXELS) {
            throw invalid("The signature is empty or too short. Please sign again.");
        }
        return png;
    }

    // Điểm ảnh "mực": đủ đậm (alpha >= 128) và tối (độ sáng < 160). Nền trong suốt hoặc trắng không tính.
    static int countInk(BufferedImage image) {
        int ink = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                if (a >= 128 && (r * 299 + g * 587 + b * 114) / 1000 < 160) {
                    ink++;
                }
            }
        }
        return ink;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_SIGNATURE_IMAGE, message);
    }
}
