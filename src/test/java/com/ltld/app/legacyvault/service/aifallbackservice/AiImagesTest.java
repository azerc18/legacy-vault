package com.ltld.app.legacyvault.service.aifallbackservice;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiImagesTest {

    /** PDF có lớp chữ (như trích lục khai tử bản điện tử). */
    static byte[] textPdf(String text, int pages) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** PDF scan: chỉ có trang trắng, không có chữ. */
    static byte[] scannedPdf() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }

    static byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void pdfText_readsTextLayer_andCuts() throws Exception {
        byte[] pdf = textPdf("TRICH LUC KHAI TU - Tran Long Van", 1);
        assertThat(AiImages.pdfText(pdf, 1000)).contains("TRICH LUC KHAI TU");
        assertThat(AiImages.pdfText(pdf, 5)).hasSize(5);
    }

    @Test
    void pdfText_scannedPdf_isEmpty() throws Exception {
        assertThat(AiImages.pdfText(scannedPdf(), 1000)).isEmpty();
    }

    @Test
    void pdfPagesAsJpeg_rendersAtMostMaxPages_asJpeg() throws Exception {
        List<byte[]> pages = AiImages.pdfPagesAsJpeg(textPdf("x", 3), 2);
        assertThat(pages).hasSize(2);
        BufferedImage first = ImageIO.read(new ByteArrayInputStream(pages.get(0)));
        assertThat(Math.max(first.getWidth(), first.getHeight())).isLessThanOrEqualTo(AiImages.MAX_EDGE_PX);
        assertThat(pages.get(0)[0]).isEqualTo((byte) 0xFF);   // JPEG bắt đầu bằng FF D8
        assertThat(pages.get(0)[1]).isEqualTo((byte) 0xD8);
    }

    @Test
    void shrinkToJpeg_limitsLongEdge_keepsRatio_andHandlesTransparency() throws Exception {
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(AiImages.shrinkToJpeg(png(3200, 1600))));
        assertThat(out.getWidth()).isEqualTo(AiImages.MAX_EDGE_PX);
        assertThat(out.getHeight()).isEqualTo(800);

        BufferedImage small = ImageIO.read(new ByteArrayInputStream(AiImages.shrinkToJpeg(png(400, 300))));
        assertThat(small.getWidth()).isEqualTo(400);   // ảnh nhỏ không bị phóng to
    }

    @Test
    void shrinkToJpeg_notAnImage_throws() {
        assertThatThrownBy(() -> AiImages.shrinkToJpeg("%PDF-1.4".getBytes()))
                .isInstanceOf(java.io.IOException.class);
    }

    // ===================== File cố tình khai kích thước cực lớn (chống hết bộ nhớ) =====================

    /** PNG chỉ có header khai {@code w x h} px RGB, phần dữ liệu gần như rỗng: vài chục byte, giải nén ra rất lớn. */
    static byte[] pngHeaderOnly(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(out);
        d.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        DataOutputStream i = new DataOutputStream(ihdr);
        i.writeInt(w);
        i.writeInt(h);
        i.write(new byte[]{8, 2, 0, 0, 0});   // 8 bit, RGB
        chunk(d, "IHDR", ihdr.toByteArray());
        Deflater deflater = new Deflater();
        deflater.setInput(new byte[1 + 3]);
        deflater.finish();
        byte[] buf = new byte[64];
        int n = deflater.deflate(buf);
        deflater.end();
        chunk(d, "IDAT", java.util.Arrays.copyOf(buf, n));
        chunk(d, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(DataOutputStream d, String type, byte[] data) throws IOException {
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        d.writeInt(data.length);
        d.write(t);
        d.write(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        d.writeInt((int) crc.getValue());
    }

    /** PDF 1 trang khổ {@code pt x pt} (PDF cho phép tới 14400 pt = 200 inch), file chỉ vài trăm byte. */
    static byte[] hugePagePdf(float pt) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage(new PDRectangle(pt, pt)));
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void pdfPagesAsJpeg_hugePage_isRenderedSmall_notAt150Dpi() throws Exception {
        byte[] pdf = hugePagePdf(14_400);
        assertThat(pdf.length).isLessThan(2_000);
        // Trước đây: 150 DPI -> 30000x30000 px -> OutOfMemoryError
        List<byte[]> pages = AiImages.pdfPagesAsJpeg(pdf, 2);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(pages.get(0)));
        assertThat(img.getWidth()).isLessThanOrEqualTo(AiImages.MAX_EDGE_PX);
        assertThat(img.getHeight()).isLessThanOrEqualTo(AiImages.MAX_EDGE_PX);
    }

    @Test
    void renderScale_keeps150Dpi_forSmallPages_andCapsLongEdge() {
        // A4 ở 150 DPI là 1240x1754 px, cạnh dài > 1600 nên thu lại vừa 1600
        assertThat(AiImages.renderScale(595, 842)).isEqualTo(AiImages.MAX_EDGE_PX / 842f);
        assertThat(AiImages.renderScale(200, 300)).isEqualTo(150f / 72f);         // trang nhỏ giữ 150 DPI
        assertThat(AiImages.renderScale(14_400, 14_400) * 14_400).isLessThanOrEqualTo(AiImages.MAX_EDGE_PX + 0.5f);
        assertThat(AiImages.renderScale(0, 0)).isEqualTo(150f / 72f);
    }

    @Test
    void shrinkToJpeg_imageDeclaringTooManyPixels_isRejected_withoutDecoding() throws Exception {
        byte[] bomb = pngHeaderOnly(20_000, 20_000);   // 400 MP, giải nén cần ~1,2GB
        assertThat(bomb.length).isLessThan(200);
        assertThatThrownBy(() -> AiImages.shrinkToJpeg(bomb))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void shrinkToJpeg_largeButAllowedImage_isSubsampled_toMaxEdge() throws Exception {
        assertThat(AiImages.subsamplingStep(8000, 2000)).isEqualTo(5);
        assertThat(AiImages.subsamplingStep(1600, 900)).isEqualTo(1);
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(AiImages.shrinkToJpeg(png(8000, 2000))));
        assertThat(out.getWidth()).isEqualTo(AiImages.MAX_EDGE_PX);
        assertThat(out.getHeight()).isEqualTo(400);
    }

    /**
     * PDF khổ A4 nhúng 1 ảnh FlateDecode khai {@code w x h} px. Dữ liệu ảnh chỉ là vài byte nén: kích thước khai
     * phải bị chặn trước khi PDFBox giải nén (ảnh thật 20000x20000 RGB nén toàn số 0 chỉ khoảng 1-2MB).
     */
    static byte[] pdfWithEmbeddedImage(int w, int h) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            ByteArrayOutputStream z = new ByteArrayOutputStream();
            try (var dz = new java.util.zip.DeflaterOutputStream(z)) {
                dz.write(new byte[3 * w]);   // 1 hàng
            }
            PDImageXObject img = new PDImageXObject(doc, new ByteArrayInputStream(z.toByteArray()),
                    COSName.FLATE_DECODE, w, h, 8, PDDeviceRGB.INSTANCE);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(img, 50, 50, 200, 200);
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void pdfPagesAsJpeg_embeddedImageDeclaringTooManyPixels_isRejected_beforeDecoding() throws Exception {
        byte[] pdf = pdfWithEmbeddedImage(20_000, 20_000);
        assertThat(pdf.length).isLessThan(5_000);
        assertThatThrownBy(() -> AiImages.pdfPagesAsJpeg(pdf, 2))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Embedded image too large");
    }

    @Test
    void pdfPagesAsJpeg_normalEmbeddedImage_stillRenders() throws Exception {
        List<byte[]> pages = AiImages.pdfPagesAsJpeg(pdfWithEmbeddedImage(800, 600), 2);
        assertThat(pages).hasSize(1);
    }
}
