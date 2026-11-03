package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.ltld.app.legacyvault.dto.beneficiarydto.InheritanceDossierData;
import com.ltld.app.legacyvault.enums.AllocationMode;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.DocumentType;
import com.ltld.app.legacyvault.enums.TransferMechanism;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class InheritanceDossierPdfServiceTest {

    private final InheritanceDossierPdfService service = assertDoesNotThrow(InheritanceDossierPdfService::new);

    private InheritanceDossierData sample(boolean withSignature) {
        var sig = withSignature
                ? new InheritanceDossierData.Signature("Trần Văn Kiểm", "verifier@example.com", "OTP",
                "a3f1".repeat(16), LocalDateTime.now(), UUID.randomUUID())
                : null;
        return new InheritanceDossierData(UUID.randomUUID(), "Di sản gia đình Nguyễn",
                LocalDateTime.now().minusDays(3), LocalDateTime.now().plusDays(57),
                new InheritanceDossierData.Person("Nguyễn Văn Ông", "owner@example.com"),
                new InheritanceDossierData.Person("Nguyễn Thị Hưởng", "ben@example.com"),
                new InheritanceDossierData.Person("Lê Thi Hành", "exec@example.com"),
                sig,
                List.of(new InheritanceDossierData.AssetLine("Vietcombank CN Sài Gòn", AssetType.BANK_ACCOUNT,
                                TransferMechanism.LEGAL_CLAIM_DOSSIER, AllocationMode.PROPORTIONAL,
                                new BigDecimal("50.00"), "Sổ tiết kiệm", false, Map.of("institutionName", "Vietcombank", "branchName", "CN Sài Gòn", "accountNumber", "****6789")),
                        new InheritanceDossierData.AssetLine("Ví MetaMask", AssetType.CRYPTO_WALLET,
                                TransferMechanism.CRYPTOGRAPHIC_SPLIT_KEY, AllocationMode.EXCLUSIVE,
                                new BigDecimal("100.00"), null, true,Map.of())),
                List.of(new InheritanceDossierData.DocumentLine("di-chuc-2026.pdf", DocumentType.WILL, LocalDateTime.now())),
                LocalDateTime.now());
    }

    private static String text(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        PdfTextExtractor ex = new PdfTextExtractor(reader);
        StringBuilder sb = new StringBuilder();
        for (int p = 1; p <= reader.getNumberOfPages(); p++) sb.append(ex.getTextFromPage(p));
        return sb.toString();
    }

    @Test
    void render_producesPdf_withVietnameseText_andAllSections() throws Exception {
        byte[] pdf = service.render(sample(true));
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        String text = text(pdf);
        assertThat(text).contains("Di sản gia đình Nguyễn", "Nguyễn Thị Hưởng", "Vietcombank CN Sài Gòn",
                "50%", "nhận thay", "Trần Văn Kiểm", "di-chuc-2026.pdf");
    }

    @Test
    void render_withoutSignature_andNoAssets_stillWorks() throws Exception {
        var d = sample(false);
        var empty = new InheritanceDossierData(d.vaultId(), d.vaultName(), d.unlockedAt(), d.claimDeadlineAt(),
                d.owner(), d.beneficiary(), null, null, List.of(), List.of(), d.generatedAt());
        String text = text(service.render(empty));
        assertThat(text).contains("Chưa có chữ ký", "Không có tài sản");
    }
}
