package com.ltld.app.legacyvault.service.legalverificationservice;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

/** PNG giả lập ảnh canvas chữ ký tay (nền trong suốt, nét đen). */
final class SignaturePngFixture {

    private SignaturePngFixture() {
    }

    /** Một nét ký dạng sóng đủ dài. */
    static byte[] signed(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(3f));
        int prevX = 20;
        int prevY = height / 2;
        for (int x = 20; x < width - 20; x += 6) {
            int y = (int) (height / 2 + Math.sin(x / 15.0) * height / 4);
            g.drawLine(prevX, prevY, x, y);
            prevX = x;
            prevY = y;
        }
        g.dispose();
        return png(img);
    }

    /** Khung trống (chỉ nền trắng đục), như khi bấm ký mà chưa vẽ. */
    static byte[] blank(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return png(img);
    }

    static String dataUrl(byte[] png) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }

    private static byte[] png(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
