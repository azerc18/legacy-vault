package com.ltld.app.legacyvault.service.aifallbackservice;

/** Tạo PDF / ảnh mẫu cho test ở package khác (AiImagesTest là package-private). */
public final class AiImagesTestSupport {
    private AiImagesTestSupport() {
    }

    public static byte[] textPdf(String text) throws Exception {
        return AiImagesTest.textPdf(text, 1);
    }

    public static byte[] scannedPdf() throws Exception {
        return AiImagesTest.scannedPdf();
    }

    public static byte[] png(int w, int h) throws Exception {
        return AiImagesTest.png(w, h);
    }
}
