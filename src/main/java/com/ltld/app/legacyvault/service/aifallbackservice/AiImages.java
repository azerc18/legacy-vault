package com.ltld.app.legacyvault.service.aifallbackservice;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Chuẩn bị tài liệu cho AI dự phòng: lấy chữ trong PDF, render trang PDF scan thành ảnh, thu nhỏ ảnh.
 * Mọi thứ chỉ nằm trong bộ nhớ, không ghi ra đĩa.
 */
public final class AiImages {

    /** Cạnh dài tối đa của ảnh gửi đi: đủ đọc chữ trên giấy tờ, và giữ base64 dưới giới hạn 4MB của Groq. */
    static final int MAX_EDGE_PX = 1600;
    /**
     * Số điểm ảnh tối đa của ảnh gốc (100 MP, ảnh điện thoại thường 12-50 MP). Ảnh khai lớn hơn bị từ chối mà không
     * giải nén: PNG nén cao vài trăm KB có thể khai 20000x20000 px, giải nén ra hơn 1GB và làm JVM hết bộ nhớ.
     */
    static final long MAX_SOURCE_PIXELS = 100_000_000L;
    /** Chỉ lấy chữ ở vài trang đầu: giấy chứng tử chỉ 1-2 trang, PDF nhiều trang không tốn thời gian vô ích. */
    static final int MAX_TEXT_PAGES = 5;
    private static final float JPEG_QUALITY = 0.85f;
    private static final int RENDER_DPI = 150;

    private AiImages() {
    }

    /** Chữ trong PDF (lớp text, {@link #MAX_TEXT_PAGES} trang đầu); PDF scan không có chữ thì trả chuỗi rỗng. Cắt ở {@code maxChars}. */
    public static String pdfText(byte[] pdf, int maxChars) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(MAX_TEXT_PAGES);
            String text = stripper.getText(doc);
            String t = text == null ? "" : text.strip();
            return t.length() > maxChars ? t.substring(0, maxChars) : t;
        }
    }

    /**
     * Render tối đa {@code maxPages} trang đầu thành JPEG đã thu nhỏ.
     * Tỉ lệ render tính theo khổ trang để ảnh không bao giờ vượt {@link #MAX_EDGE_PX}: PDF cho phép trang tới
     * 200x200 inch, render cố định 150 DPI sẽ ra ảnh 30000x30000 px (vài GB RAM) chỉ từ một file vài trăm byte.
     */
    public static List<byte[]> pdfPagesAsJpeg(byte[] pdf, int maxPages) throws IOException {
        List<byte[]> out = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            // Ảnh nhúng lớn hơn cần thiết thì PDFBox đọc thưa (subsample), không giải nén hết cỡ
            renderer.setSubsamplingAllowed(true);
            int pages = Math.min(maxPages, doc.getNumberOfPages());
            for (int i = 0; i < pages; i++) {
                PDRectangle box = doc.getPage(i).getCropBox();
                out.add(toJpeg(renderer.renderImage(i, renderScale(box.getWidth(), box.getHeight()), ImageType.RGB)));
            }
        }
        return out;
    }

    /** Tỉ lệ render (1 = 72 DPI): 150 DPI, nhưng nhỏ lại để cạnh dài không quá {@link #MAX_EDGE_PX}. */
    static float renderScale(float widthPt, float heightPt) {
        float longEdge = Math.max(widthPt, heightPt);
        float dpiScale = RENDER_DPI / 72f;
        if (!(longEdge > 0)) {
            return dpiScale;
        }
        return Math.min(dpiScale, MAX_EDGE_PX / longEdge);
    }

    /**
     * Ảnh JPG/PNG bất kỳ -> JPEG cạnh dài tối đa {@link #MAX_EDGE_PX}. Không đọc được ảnh, hoặc ảnh khai quá
     * {@link #MAX_SOURCE_PIXELS}, thì báo lỗi. Kích thước được đọc từ header trước, ảnh lớn được đọc thưa
     * (subsampling) nên bộ nhớ dùng chỉ cỡ vài lần ảnh đích, không phải cỡ ảnh gốc.
     */
    public static byte[] shrinkToJpeg(byte[] image) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new IOException("Unsupported image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long w = reader.getWidth(0);
                long h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w * h > MAX_SOURCE_PIXELS) {
                    throw new IOException("Image too large: " + w + "x" + h);
                }
                ImageReadParam param = reader.getDefaultReadParam();
                int step = subsamplingStep(w, h);
                if (step > 1) {
                    param.setSourceSubsampling(step, step, 0, 0);
                }
                BufferedImage img = reader.read(0, param);
                if (img == null) {
                    throw new IOException("Unsupported image");
                }
                return toJpeg(img);
            } finally {
                reader.dispose();
            }
        }
    }

    /** Bước đọc thưa để ảnh sau khi đọc vẫn có cạnh dài >= {@link #MAX_EDGE_PX} (rồi {@link #toJpeg} thu nhỏ mịn). */
    static int subsamplingStep(long width, long height) {
        return (int) Math.max(1, Math.max(width, height) / MAX_EDGE_PX);
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
