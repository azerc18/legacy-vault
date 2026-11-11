package com.ltld.app.legacyvault.utility;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Sinh ảnh PNG mã QR (đen trên nền trắng) từ một chuỗi, dùng cho mail nhắc check-in DMS.
 * Chỉ dùng zxing core + ImageIO của JDK, không cần zxing javase.
 */
public final class QrCodeImage {

    private QrCodeImage() {
    }

    /**
     * @param content nội dung mã hoá (thường là URL)
     * @param sizePx  cạnh ảnh tính bằng pixel (đã gồm lề trắng)
     * @return byte PNG
     * @throws IllegalStateException khi không sinh được ảnh
     */
    public static byte[] png(String content, int sizePx) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 2,
                            EncodeHintType.CHARACTER_SET, "UTF-8"));
            int w = matrix.getWidth();
            int h = matrix.getHeight();
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Cannot generate QR code", e);
        }
    }
}
