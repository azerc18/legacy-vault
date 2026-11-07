package com.ltld.app.legacyvault.service.paymentservice;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.ltld.app.legacyvault.dto.paymentdto.PaymentReceiptData;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PaymentReceiptPdfServiceTest {

    private final PaymentReceiptPdfService service = assertDoesNotThrow(PaymentReceiptPdfService::new);

    private static PaymentReceiptData sample(String fullName, String bankCode) {
        return new PaymentReceiptData("LV-20261107-1A2B3C4D", UUID.randomUUID(), fullName, "owner@example.com",
                "VIP_YEAR", "VIP Yearly", 365, 990_000L, "14512345", bankCode,
                LocalDateTime.of(2026, 11, 7, 9, 55), LocalDateTime.of(2026, 11, 7, 10, 0), LocalDateTime.of(2026, 11, 7, 10, 0));
    }

    private static String text(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        PdfTextExtractor ex = new PdfTextExtractor(reader);
        StringBuilder sb = new StringBuilder();
        for (int p = 1; p <= reader.getNumberOfPages(); p++) sb.append(ex.getTextFromPage(p));
        return sb.toString();
    }

    @Test
    void render_producesPdf_withVietnameseText_andPaymentDetails() throws Exception {
        byte[] pdf = service.render(sample("Nguyễn Văn Ông", "NCB"));

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        String text = text(pdf);
        assertThat(text).contains("BIÊN LAI THANH TOÁN", "LV-20261107-1A2B3C4D", "Nguyễn Văn Ông",
                "owner@example.com", "VIP Yearly", "365 ngày", "990.000 ₫", "14512345", "NCB",
                "07/11/2026 10:00", "GTGT");
    }

    @Test
    void render_handlesMissingOptionalFields() throws Exception {
        byte[] pdf = service.render(sample(null, null));
        assertThat(text(pdf)).contains("owner@example.com", "—");
    }
}
