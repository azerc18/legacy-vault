package com.ltld.app.legacyvault.service.aifallbackservice;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

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
}
