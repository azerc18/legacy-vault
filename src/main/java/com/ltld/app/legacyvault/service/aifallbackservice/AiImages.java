package com.ltld.app.legacyvault.service.aifallbackservice;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Chuẩn bị tài liệu cho AI dự phòng: lấy chữ trong PDF, render trang PDF scan thành ảnh, thu nhỏ ảnh.
 * Mọi thứ chỉ nằm trong bộ nhớ, không ghi ra đĩa.
 */
public final class AiImages {

    /** Cạnh dài tối đa của ảnh gửi đi: đủ đọc chữ trên giấy tờ, và giữ base64 dưới giới hạn 4MB của Groq. */
    static final int MAX_EDGE_PX = 1600;
    private static final float JPEG_QUALITY = 0.85f;
    private static final int RENDER_DPI = 150;

    private AiImages() {
    }

    /** Chữ trong PDF (lớp text); PDF scan không có chữ thì trả chuỗi rỗng. Cắt ở {@code maxChars}. */
    public static String pdfText(byte[] pdf, int maxChars) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            String t = text == null ? "" : text.strip();
            return t.length() > maxChars ? t.substring(0, maxChars) : t;
        }
    }

    /** Render tối đa {@code maxPages} trang đầu thành JPEG đã thu nhỏ. */
    public static List<byte[]> pdfPagesAsJpeg(byte[] pdf, int maxPages) throws IOException {
        List<byte[]> out = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pages = Math.min(maxPages, doc.getNumberOfPages());
            for (int i = 0; i < pages; i++) {
                out.add(toJpeg(renderer.renderImageWithDPI(i, RENDER_DPI, ImageType.RGB)));
            }
        }
        return out;
    }

    /** Ảnh JPG/PNG bất kỳ -> JPEG cạnh dài tối đa {@link #MAX_EDGE_PX}. Không đọc được ảnh thì báo lỗi. */
    public static byte[] shrinkToJpeg(byte[] image) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(image));
        if (img == null) {
            throw new IOException("Unsupported image");
        }
        return toJpeg(img);
    }

    static byte[] toJpeg(BufferedImage src) throws IOException {
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.min(1.0, (double) MAX_EDGE_PX / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        // Vẽ lại lên nền RGB: PNG có kênh trong suốt ghi JPEG trực tiếp sẽ hỏng màu
        BufferedImage rgb = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, nw, nh);
            g.drawImage(src, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
            writer.setOutput(ios);
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(rgb, null, null), p);
        } finally {
            writer.dispose();
        }
        return bos.toByteArray();
    }
}
