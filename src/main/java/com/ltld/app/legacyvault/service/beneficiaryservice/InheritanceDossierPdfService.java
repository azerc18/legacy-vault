package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.ltld.app.legacyvault.dto.beneficiarydto.InheritanceDossierData;
import com.ltld.app.legacyvault.dto.beneficiarydto.InheritanceDossierData.AssetLine;
import com.ltld.app.legacyvault.enums.AllocationMode;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.DocumentType;
import com.ltld.app.legacyvault.enums.TransferMechanism;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * X4: "Bản Kê Khai & Hướng Dẫn Thừa Kế Tài Sản Số" (PDF, tiếng Việt, font NotoSans nhúng).
 * Chỉ vẽ từ InheritanceDossierData; không đọc DB, không giải mã, không in trường bí mật.
 */
@Component
public class InheritanceDossierPdfService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color PRIMARY = new Color(0x00, 0x23, 0x6B);
    private static final Color MUTED = new Color(0x5F, 0x63, 0x68);
    private static final Color HEAD_BG = new Color(0xE8, 0xED, 0xF7);

    private static final Map<AssetType, String> ASSET_TYPE = Map.ofEntries(
            Map.entry(AssetType.BANK_ACCOUNT, "Tài khoản ngân hàng"),
            Map.entry(AssetType.EXCHANGE_ACCOUNT, "Tài khoản sàn giao dịch"),
            Map.entry(AssetType.CRYPTO_WALLET, "Ví tiền mã hoá"),
            Map.entry(AssetType.E_WALLET, "Ví điện tử"),
            Map.entry(AssetType.SOCIAL_MEDIA, "Mạng xã hội"),
            Map.entry(AssetType.EMAIL, "Email"),
            Map.entry(AssetType.DOCUMENT, "Tài liệu"),
            Map.entry(AssetType.OTHER, "Khác"));
    private static final Map<TransferMechanism, String> MECHANISM = Map.of(
            TransferMechanism.LEGAL_CLAIM_DOSSIER, "Hồ sơ khai nhận pháp lý",
            TransferMechanism.CRYPTOGRAPHIC_SPLIT_KEY, "Chuyển giao khoá mật mã",
            TransferMechanism.EXECUTOR_DISPOSITION, "Người thi hành xử lý theo di nguyện");
    private static final Map<AllocationMode, String> MODE = Map.of(
            AllocationMode.EXCLUSIVE, "Độc quyền",
            AllocationMode.PROPORTIONAL, "Theo tỷ lệ",
            AllocationMode.JOINT_CONSENSUS, "Đồng thuận chung");
    private static final Map<DocumentType, String> DOC_TYPE = Map.of(
            DocumentType.WILL, "Di chúc",
            DocumentType.OWNERSHIP_PAPER, "Giấy tờ sở hữu",
            DocumentType.IDENTITY_CARD, "Giấy tờ tuỳ thân",
            DocumentType.OTHER, "Khác");
    private static final Map<String, String> FIELD_LABEL = Map.ofEntries(
            // Ngân hàng / sàn
            Map.entry("institutionName", "Ngân hàng / sàn"),
            Map.entry("branchName", "Chi nhánh"),
            Map.entry("accountNumber", "Số tài khoản"),
            Map.entry("accountId", "Mã tài khoản"),
            Map.entry("estimatedValue", "Giá trị ước tính"),
            Map.entry("currency", "Tiền tệ"),
            Map.entry("legalInstruction", "Hướng dẫn của chủ di sản"),
            // Tài liệu
            Map.entry("documentTitle", "Tên tài liệu"),
            Map.entry("issuer", "Cơ quan cấp"),
            Map.entry("location", "Nơi cất bản gốc"),
            // Ví crypto
            Map.entry("walletProvider", "Ví"),
            Map.entry("blockchainNetwork", "Mạng blockchain"),
            Map.entry("walletAddress", "Địa chỉ ví"),
            // MXH / email / ví điện tử
            Map.entry("platform", "Nền tảng"),
            Map.entry("usernameOrHandle", "Tên tài khoản"),
            Map.entry("wish", "Di nguyện"),
            Map.entry("deviceOrSimLocation", "Nơi cất điện thoại / SIM / thiết bị 2FA"),
            // Khác
            Map.entry("title", "Tiêu đề"),
            Map.entry("description", "Mô tả"));

    private static final Map<String, String> VALUE_LABEL = Map.of(
            "MEMORIALIZE", "Chuyển sang trang tưởng niệm",
            "DELETE", "Xoá tài khoản",
            "TRANSFER_DATA", "Tải dữ liệu và chuyển giao");
    // Hướng dẫn thủ tục theo cơ chế (tham khảo, không thay thế tư vấn pháp lý)

    private static final Map<TransferMechanism, List<String>> PROCEDURE = Map.of(
            TransferMechanism.LEGAL_CLAIM_DOSSIER, List.of(
                    "Chuẩn bị: bản sao trích lục khai tử của người để lại di sản; di chúc (nếu có); giấy tờ tuỳ thân của người thụ hưởng; giấy tờ chứng minh quan hệ với người để lại di sản (khai sinh, đăng ký kết hôn...).",
                    "Lập văn bản khai nhận di sản hoặc văn bản thoả thuận phân chia di sản tại tổ chức hành nghề công chứng. Việc thụ lý được niêm yết công khai tại UBND cấp xã nơi thường trú cuối cùng của người để lại di sản (thường 15 ngày).",
                    "Mang văn bản đã công chứng cùng hồ sơ này đến ngân hàng / sàn giao dịch / cơ quan quản lý tài sản để làm thủ tục tất toán hoặc chuyển quyền sở hữu."),
            TransferMechanism.CRYPTOGRAPHIC_SPLIT_KEY, List.of(
                    "Khoá truy cập (seed phrase / private key) KHÔNG được in trong hồ sơ này. Truy xuất khoá trong LegacyVault sau khi xác minh danh tính.",
                    "Chuyển ngay tài sản sang ví mới do chính bạn tạo và kiểm soát; không tiếp tục dùng ví cũ.",
                    "Không chia sẻ khoá cho bất kỳ ai, kể cả người tự xưng là nhân viên hỗ trợ. Nếu tài sản nằm trên sàn có KYC, làm thủ tục như hồ sơ khai nhận pháp lý."),
            TransferMechanism.EXECUTOR_DISPOSITION, List.of(
                    "Người thi hành di sản số sẽ xử lý tài khoản theo di nguyện (tưởng niệm, xoá, hoặc tải dữ liệu) theo chính sách của từng nền tảng.",
                    "Người thụ hưởng phối hợp cung cấp trích lục khai tử và giấy tờ chứng minh quan hệ khi nền tảng yêu cầu.",
                    "Không đăng nhập vào tài khoản của người đã mất bằng mật khẩu cũ nếu nền tảng có quy trình chính thức, để tránh vi phạm điều khoản sử dụng."));

    private final BaseFont regular;
    private final BaseFont bold;

    public InheritanceDossierPdfService() throws IOException {
        this.regular = loadFont("fonts/NotoSans-Regular.ttf");
        this.bold = loadFont("fonts/NotoSans-Bold.ttf");
    }

    public byte[] render(InheritanceDossierData d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 56, 56);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(new PageNumbers());
        doc.addTitle("Hồ sơ khai nhận di sản số - " + d.vaultName());
        doc.addCreator("LegacyVault");
        doc.open();

        // ---------- Tiêu đề ----------
        Paragraph title = new Paragraph("BẢN KÊ KHAI & HƯỚNG DẪN THỪA KẾ TÀI SẢN SỐ", font(bold, 15, PRIMARY));
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);
        Paragraph sub = new Paragraph("Kho di sản: " + d.vaultName() + "   ·   Lập lúc: " + fmt(d.generatedAt())
                + "\nMã hồ sơ: " + d.vaultId(), font(regular, 9, MUTED));
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(14);
        doc.add(sub);

        // ---------- Phần 1 ----------
        doc.add(heading("1. Các bên liên quan và xác nhận pháp lý"));
        PdfPTable parties = table(new float[]{30, 70});
        row(parties, "Chủ di sản", person(d.owner()));
        row(parties, "Người thụ hưởng", person(d.beneficiary()));
        row(parties, "Người thi hành di sản số", d.executor() == null ? "Không có" : person(d.executor()));
        row(parties, "Mở khoá kho di sản", fmt(d.unlockedAt()));
        row(parties, "Hạn nhận tài sản", fmt(d.claimDeadlineAt()));
        doc.add(parties);

        if (d.signature() != null) {
            var s = d.signature();
            PdfPTable sig = table(new float[]{30, 70});
            row(sig, "Người xác minh pháp lý", s.verifierName() + " <" + s.verifierEmail() + ">");
            row(sig, "Phương thức ký", s.method());
            row(sig, "Thời điểm ký", fmt(s.signedAt()));
            row(sig, "Mã yêu cầu xác minh", String.valueOf(s.requestId()));
            PdfPCell k = cell("Mã băm chữ ký số", bold, 9); k.setBackgroundColor(HEAD_BG);
            sig.addCell(k);
            sig.addCell(cell(s.hash(), regular, 7.5f));   // hash dài -> chữ nhỏ, tự xuống dòng
            sig.setSpacingBefore(8);
            doc.add(sig);
        } else {
            doc.add(note("Chưa có chữ ký xác nhận của Người xác minh pháp lý cho kho di sản này."));
        }

        // ---------- Phần 2 ----------
        doc.add(heading("2. Danh mục tài sản được phân bổ cho bạn"));
        if (d.assets().isEmpty()) {
            doc.add(note("Không có tài sản nào đang được phân bổ cho bạn trong kho di sản này."));
        } else {
            PdfPTable t = table(new float[]{5, 37, 14, 18, 13, 13});
            for (String h : List.of("#", "Tài sản", "Loại", "Cơ chế chuyển giao", "Hình thức", "Tỷ lệ")) header(t, h);
            int i = 1;
            for (AssetLine a : d.assets()) {
                t.addCell(cell(String.valueOf(i++), regular, 9));
                StringBuilder name = new StringBuilder(a.assetName());
                if (a.publicFields() != null) {
                    a.publicFields().forEach((k, v) -> name.append("\n")
                            .append(FIELD_LABEL.getOrDefault(k, k)).append(": ")
                            .append(displayValue(k, v)));
                }
                if (a.viaFallback()) name.append("\n(nhận thay người thụ hưởng chính)");
                if (a.note() != null && !a.note().isBlank()) name.append("\nGhi chú: ").append(a.note());
                t.addCell(cell(name.toString(), regular, 9));
                t.addCell(cell(ASSET_TYPE.getOrDefault(a.type(), String.valueOf(a.type())), regular, 9));
                t.addCell(cell(MECHANISM.getOrDefault(a.mechanism(), String.valueOf(a.mechanism())), regular, 9));
                // Tài sản chưa phân bổ riêng có mode = null (Map.of không nhận key null)
                String modeText = a.mode() == null
                        ? "Chưa chia riêng"
                        : MODE.getOrDefault(a.mode(), String.valueOf(a.mode()));
                t.addCell(cell(modeText, regular, 9));
                t.addCell(cell(percent(a.sharePercent()), regular, 9));
            }
            doc.add(t);
        }

        Paragraph docsTitle = new Paragraph("Tài liệu pháp lý đi kèm trong kho di sản", font(bold, 10, PRIMARY));
        docsTitle.setSpacingBefore(10);
        doc.add(docsTitle);
        if (d.documents().isEmpty()) {
            doc.add(note("Không có tài liệu pháp lý."));
        } else {
            com.lowagie.text.List list = new com.lowagie.text.List(false, 12);
            for (var dl : d.documents()) {
                list.add(new ListItem(DOC_TYPE.getOrDefault(dl.type(), String.valueOf(dl.type())) + ": "
                        + dl.fileName() + " (tải lên " + fmt(dl.uploadedAt()) + ")", font(regular, 9, Color.BLACK)));
            }
            doc.add(list);
        }

        // ---------- Phần 3 ----------
        doc.add(heading("3. Hướng dẫn thủ tục theo cơ chế chuyển giao"));
        Set<TransferMechanism> used = new LinkedHashSet<>();
        d.assets().forEach(a -> used.add(a.mechanism()));
        if (used.isEmpty()) used.addAll(List.of(TransferMechanism.values()));
        for (TransferMechanism m : used) {
            Paragraph mh = new Paragraph(MECHANISM.get(m), font(bold, 10, Color.BLACK));
            mh.setSpacingBefore(6);
            doc.add(mh);
            com.lowagie.text.List steps = new com.lowagie.text.List(true, 14);
            PROCEDURE.get(m).forEach(s -> steps.add(new ListItem(s, font(regular, 9, Color.BLACK))));
            doc.add(steps);
        }

        Paragraph disclaimer = new Paragraph(
                "Hồ sơ do hệ thống LegacyVault lập tự động, chỉ liệt kê phần tài sản của người thụ hưởng nêu trên và không chứa "
                        + "thông tin truy cập bí mật. Nội dung hướng dẫn mang tính tham khảo, không thay thế tư vấn của luật sư hoặc công chứng viên.",
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
            // IDENTITY_H + EMBEDDED: bắt buộc để hiển thị đúng dấu tiếng Việt trên mọi máy
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

    private Paragraph note(String text) {
        Paragraph p = new Paragraph(text, font(regular, 9, MUTED));
        p.setSpacingBefore(4);
        return p;
    }

    private static PdfPTable table(float[] widths) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        return t;
    }

    private PdfPCell cell(String text, BaseFont bf, float size) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "—" : text, font(bf, size, Color.BLACK)));
        c.setPadding(5);
        c.setBorderColor(new Color(0xD0, 0xD5, 0xDD));
        return c;
    }

    private void header(PdfPTable t, String text) {
        PdfPCell c = cell(text, bold, 9);
        c.setBackgroundColor(HEAD_BG);
        t.addCell(c);
    }

    private void row(PdfPTable t, String label, String value) {
        header(t, label);
        t.addCell(cell(value, regular, 9));
    }

    private static String person(InheritanceDossierData.Person p) {
        if (p == null) return "—";
        boolean noName = p.fullName() == null || p.fullName().isBlank();
        return noName ? p.email() : p.fullName() + " <" + p.email() + ">";
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? "—" : t.format(DT);
    }

    private static String percent(BigDecimal p) {
        return p == null ? "—" : p.stripTrailingZeros().toPlainString() + "%";
    }

    // Số trang ở chân trang: "Trang n"
    private class PageNumbers extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                    new Phrase("LegacyVault · Trang " + writer.getPageNumber(), font(regular, 8, MUTED)),
                    (document.left() + document.right()) / 2, document.bottom() - 24, 0);
        }
    }

    private static String displayValue(String key, String v) {
        if ("estimatedValue".equals(key)) {
            try {
                return java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("vi-VN"))
                        .format(new java.math.BigDecimal(v));   // 150000000 -> 150.000.000
            } catch (NumberFormatException e) {
                return v;
            }
        }
        return VALUE_LABEL.getOrDefault(v, v);
    }
}