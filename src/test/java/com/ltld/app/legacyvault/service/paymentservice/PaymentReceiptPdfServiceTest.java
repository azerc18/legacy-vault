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
                LocalDateTime.of(2026, 11, 7, 9, 55), LocalDateTime.of(2026, 11, 7, 10, 0), LocalDateTime.of(2026, 11, 7, 10, 0),
                42L, "VNPay");
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
        assertThat(text).contains("HÓA ĐƠN GIÁ TRỊ GIA TĂNG", "C26TLV", "0000042", "LV-20261107-1A2B3C4D",
                "Nguyễn Văn Ông", "owner@example.com", "VIP Yearly", "365 ngày",
                "900.000 ₫", "90.000 ₫", "990.000 ₫", "Chín trăm chín mươi nghìn đồng chẵn",
                "14512345", "NCB", "07/11/2026 10:00", "MÔ PHỎNG", "giả lập");
        assertThat(new PdfReader(pdf).getNumberOfPages()).isEqualTo(1);
    }

    @Test
    void render_handlesMissingOptionalFields() throws Exception {
        byte[] pdf = service.render(sample(null, null));
        assertThat(text(pdf)).contains("owner@example.com", "—");
    }

    @Test
    void netAmount_splitsGrossPriceIntoNetAndVat() {
        assertThat(PaymentReceiptPdfService.netAmount(99_000L)).isEqualTo(90_000L);
        assertThat(PaymentReceiptPdfService.netAmount(990_000L)).isEqualTo(900_000L);
    }

    @Test
    void invoiceSerial_usesIssueYear() {
        assertThat(PaymentReceiptPdfService.invoiceSerial(LocalDateTime.of(2026, 11, 7, 10, 0))).isEqualTo("C26TLV");
    }

    @Test
    void taxAuthorityCode_isStable34Chars() {
        PaymentReceiptData d = sample("A", "NCB");
        String code = PaymentReceiptPdfService.taxAuthorityCode(d);
        assertThat(code).hasSize(34).matches("[0-9A-F]{34}");
        assertThat(PaymentReceiptPdfService.taxAuthorityCode(d)).isEqualTo(code);
    }
}
