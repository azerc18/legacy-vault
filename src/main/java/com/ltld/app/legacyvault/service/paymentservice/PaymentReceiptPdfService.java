package com.ltld.app.legacyvault.service.paymentservice;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.ltld.app.legacyvault.dto.paymentdto.PaymentReceiptData;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * T2: "Biên lai thanh toán" (PDF, tiếng Việt, font NotoSans nhúng) cho một giao dịch VNPay thành công.
 * Chỉ vẽ từ {@link PaymentReceiptData}; không đọc DB. Đây là biên lai xác nhận đã thu tiền, không phải hoá đơn GTGT.
 */
@Component
public class PaymentReceiptPdfService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color PRIMARY = new Color(0x00, 0x23, 0x6B);
    private static final Color MUTED = new Color(0x5F, 0x63, 0x68);
    private static final Color HEAD_BG = new Color(0xE8, 0xED, 0xF7);
    private static final Color BORDER = new Color(0xD0, 0xD5, 0xDD);

    private final BaseFont regular;
    private final BaseFont bold;

    public PaymentReceiptPdfService() throws IOException {
        this.regular = loadFont("fonts/NotoSans-Regular.ttf");
        this.bold = loadFont("fonts/NotoSans-Bold.ttf");
    }

    public byte[] render(PaymentReceiptData d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 56, 56);
        PdfWriter.getInstance(doc, out);
        doc.addTitle("Biên lai thanh toán " + d.receiptNo());
        doc.addCreator("LegacyVault");
        doc.open();

        Paragraph brand = new Paragraph("LegacyVault", font(bold, 11, PRIMARY));
        brand.setAlignment(Element.ALIGN_CENTER);
        doc.add(brand);
        Paragraph title = new Paragraph("BIÊN LAI THANH TOÁN", font(bold, 16, PRIMARY));
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);
        Paragraph sub = new Paragraph("Số biên lai: " + d.receiptNo() + "   ·   Ngày lập: " + fmt(d.issuedAt()),
                font(regular, 9, MUTED));
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(14);
        doc.add(sub);

        doc.add(heading("1. Thông tin khách hàng"));
        PdfPTable customer = table(new float[]{30, 70});
        row(customer, "Họ và tên", blankToDash(d.customerName()));
        row(customer, "Email", d.customerEmail());
        doc.add(customer);

        doc.add(heading("2. Chi tiết dịch vụ"));
        PdfPTable items = table(new float[]{8, 46, 20, 26});
        for (String h : new String[]{"#", "Dịch vụ", "Thời hạn", "Thành tiền"}) header(items, h);
        items.addCell(cell("1", regular, 9, Element.ALIGN_LEFT));
        String service = (d.planName() != null ? d.planName() : "Gói LegacyVault")
                + (d.planCode() != null ? " (" + d.planCode() + ")" : "");
        items.addCell(cell(service, regular, 9, Element.ALIGN_LEFT));
        items.addCell(cell(d.durationDays() != null ? d.durationDays() + " ngày" : "—", regular, 9, Element.ALIGN_LEFT));
        items.addCell(cell(vnd(d.amountVnd()), regular, 9, Element.ALIGN_RIGHT));
        PdfPCell totalLabel = cell("Tổng cộng", bold, 10, Element.ALIGN_RIGHT);
        totalLabel.setColspan(3);
        totalLabel.setBackgroundColor(HEAD_BG);
        items.addCell(totalLabel);
        PdfPCell total = cell(vnd(d.amountVnd()), bold, 10, Element.ALIGN_RIGHT);
        total.setBackgroundColor(HEAD_BG);
        items.addCell(total);
        doc.add(items);

        doc.add(heading("3. Thông tin thanh toán"));
        PdfPTable pay = table(new float[]{30, 70});
        row(pay, "Phương thức", "Cổng thanh toán VNPay");
        row(pay, "Ngân hàng", blankToDash(d.vnpBankCode()));
        row(pay, "Mã giao dịch VNPay", blankToDash(d.vnpTransactionNo()));
        row(pay, "Mã đơn hàng", String.valueOf(d.transactionId()));
        row(pay, "Thời điểm tạo đơn", fmt(d.createdAt()));
        row(pay, "Thời điểm thanh toán", fmt(d.paidAt()));
        row(pay, "Trạng thái", "Đã thanh toán");
        doc.add(pay);

        Paragraph disclaimer = new Paragraph(
                "Biên lai do hệ thống LegacyVault lập tự động, xác nhận đã nhận đủ số tiền nêu trên qua VNPay. "
                        + "Đây không phải hoá đơn giá trị gia tăng (GTGT).",
                font(regular, 8, MUTED));
        disclaimer.setSpacingBefore(16);
        doc.add(disclaimer);

        doc.close();
        return out.toByteArray();
    }

    // ---------- helpers ----------

    private static BaseFont loadFont(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            byte[] bytes = in.readAllBytes();
            // IDENTITY_H + EMBEDDED: hiển thị đúng dấu tiếng Việt trên mọi máy
            return BaseFont.createFont(path.substring(path.lastIndexOf('/') + 1),
                    BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
        }
    }

    private static Font font(BaseFont bf, float size, Color color) {
        return new Font(bf, size, Font.NORMAL, color);
    }

    private Paragraph heading(String text) {
        Paragraph p = new Paragraph(text, font(bold, 12, PRIMARY));
        p.setSpacingBefore(14);
        p.setSpacingAfter(6);
        return p;
    }

    private static PdfPTable table(float[] widths) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        return t;
    }

    private PdfPCell cell(String text, BaseFont bf, float size, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "—" : text, font(bf, size, Color.BLACK)));
        c.setPadding(5);
        c.setBorderColor(BORDER);
        c.setHorizontalAlignment(align);
        return c;
    }

    private void header(PdfPTable t, String text) {
        PdfPCell c = cell(text, bold, 9, Element.ALIGN_LEFT);
        c.setBackgroundColor(HEAD_BG);
        t.addCell(c);
    }

    private void row(PdfPTable t, String label, String value) {
        header(t, label);
        t.addCell(cell(value, regular, 9, Element.ALIGN_LEFT));
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? "—" : t.format(DT);
    }

    private static String blankToDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    static String vnd(long amount) {
        return NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(amount) + " ₫";
    }
}
