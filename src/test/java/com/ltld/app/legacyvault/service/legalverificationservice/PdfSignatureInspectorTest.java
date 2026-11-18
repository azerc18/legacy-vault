package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.dto.legalverificationdto.PdfSignatureReportDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.PdfSignatureReportDto.Overall;
import com.ltld.app.legacyvault.dto.legalverificationdto.PdfSignatureReportDto.SignatureInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** V2: kiểm chữ ký PAdES thật (không mock phần mật mã). */
class PdfSignatureInspectorTest {

    private static byte[] signedPdf;
    private final PdfSignatureInspector inspector = new PdfSignatureInspector();
    private final UUID requestId = UUID.randomUUID();

    @BeforeAll
    static void sign() throws Exception {
        signedPdf = SignedPdfFixture.sign(SignedPdfFixture.unsignedPdf("Giay chung tu - ban sao"), -30, 365);
    }

    @Test
    void signedPdf_selfSigned_allChecksPassButNeedsReview() {
        PdfSignatureReportDto r = inspector.inspect(requestId, signedPdf);

        assertThat(r.getDocumentKind()).isEqualTo(PdfSignatureReportDto.DocumentKind.PDF);
        // Fixture dùng chứng thư tự ký: mọi kiểm tra kỹ thuật đạt nhưng không được banner xanh (review #295 m1)
        assertThat(r.getOverall()).isEqualTo(Overall.HAS_UNVERIFIED);
        assertThat(r.getSignatureCount()).isEqualTo(1);
        SignatureInfo s = r.getSignatures().get(0);
        assertThat(s.getFieldName()).isEqualTo("CertSignature");
        assertThat(s.getSubFilter()).isEqualTo("adbe.pkcs7.detached");
        assertThat(s.getSignerName()).isEqualTo(SignedPdfFixture.SIGNER_CN);
        assertThat(s.getSignerOrganization()).isEqualTo(SignedPdfFixture.SIGNER_O);
        assertThat(s.getDigestAlgorithm()).isEqualTo("SHA-256");
        assertThat(s.getIntegrityValid()).isTrue();
        assertThat(s.getCoversWholeDocument()).isTrue();
        assertThat(s.getCertificateValidAtSigning()).isTrue();
        assertThat(s.getSelfSigned()).isTrue();
        assertThat(s.getSignedAt()).isNotNull();
        assertThat(s.getReason()).isEqualTo("Chung thuc ban sao");
        assertThat(s.getLocation()).isEqualTo("TP.HCM");
        assertThat(s.getProblem()).isNull();
    }

    @Test
    void caIssuedCertificate_allChecksPass_greenOverall() throws Exception {
        byte[] pdf = SignedPdfFixture.sign(SignedPdfFixture.unsignedPdf("ban sao"), -30, 365, true);

        PdfSignatureReportDto r = inspector.inspect(requestId, pdf);

        SignatureInfo s = r.getSignatures().get(0);
        assertThat(s.getSelfSigned()).isFalse();
        assertThat(s.getIssuer()).isEqualTo(SignedPdfFixture.CA_CN);
        assertThat(s.getIntegrityValid()).isTrue();
        assertThat(r.getOverall()).isEqualTo(Overall.ALL_VALID);
    }

    @Test
    void byteInsideSignedRangeChanged_integrityFails() {
        byte[] tampered = signedPdf.clone();
        // Đổi "TP.HCM" thành "TP.HCN" trong /Location: cùng độ dài, PDF vẫn đọc được, nhưng nằm trong vùng đã ký
        int idx = indexOf(tampered, "TP.HCM".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(idx).isPositive();
        tampered[idx + 5] = 'N';

        PdfSignatureReportDto r = inspector.inspect(requestId, tampered);

        assertThat(r.getOverall()).isEqualTo(Overall.HAS_INVALID);
        assertThat(r.getSignatures().get(0).getIntegrityValid()).isFalse();
        assertThat(r.getSignatures().get(0).getLocation()).isEqualTo("TP.HCN");
    }

    @Test
    void bytesAppendedAfterSigning_notWholeDocument() {
        byte[] appended = Arrays.copyOf(signedPdf, signedPdf.length + 30);
        byte[] extra = "\n% added after signing\n".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(extra, 0, appended, signedPdf.length, extra.length);

        PdfSignatureReportDto r = inspector.inspect(requestId, appended);

        SignatureInfo s = r.getSignatures().get(0);
        assertThat(s.getIntegrityValid()).isTrue();
        assertThat(s.getCoversWholeDocument()).isFalse();
        assertThat(r.getOverall()).isEqualTo(Overall.HAS_UNVERIFIED);
    }

    @Test
    void certificateExpiredBeforeSigning_flagged() throws Exception {
        byte[] pdf = SignedPdfFixture.sign(SignedPdfFixture.unsignedPdf("x"), -400, -10);

        PdfSignatureReportDto r = inspector.inspect(requestId, pdf);

        SignatureInfo s = r.getSignatures().get(0);
        assertThat(s.getIntegrityValid()).isTrue();
        assertThat(s.getCertificateValidAtSigning()).isFalse();
        assertThat(r.getOverall()).isEqualTo(Overall.HAS_UNVERIFIED);
    }

    @Test
    void unsignedPdf_noSignature() throws Exception {
        PdfSignatureReportDto r = inspector.inspect(requestId, SignedPdfFixture.unsignedPdf("plain"));

        assertThat(r.getOverall()).isEqualTo(Overall.NO_SIGNATURE);
        assertThat(r.getSignatures()).isEmpty();
    }

    @Test
    void image_isNotPdf() {
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01};
        PdfSignatureReportDto r = inspector.inspect(requestId, jpg);

        assertThat(r.getDocumentKind()).isEqualTo(PdfSignatureReportDto.DocumentKind.IMAGE);
        assertThat(r.getOverall()).isEqualTo(Overall.NOT_PDF);
    }

    @Test
    void brokenPdf_unreadable() {
        PdfSignatureReportDto r = inspector.inspect(requestId, "%PDF-1.4 garbage".getBytes(StandardCharsets.US_ASCII));

        assertThat(r.getOverall()).isEqualTo(Overall.UNREADABLE);
    }

    @Test
    void overall_rules() {
        SignatureInfo ok = SignatureInfo.builder().integrityValid(true).coversWholeDocument(true)
                .certificateValidAtSigning(true).selfSigned(false).build();
        SignatureInfo noTime = SignatureInfo.builder().integrityValid(true).coversWholeDocument(true)
                .certificateValidAtSigning(null).selfSigned(false).build();
        SignatureInfo selfSigned = SignatureInfo.builder().integrityValid(true).coversWholeDocument(true)
                .certificateValidAtSigning(true).selfSigned(true).build();
        SignatureInfo unknown = SignatureInfo.builder().problem("SIGNATURE_ALGORITHM_UNSUPPORTED").build();
        SignatureInfo bad = SignatureInfo.builder().integrityValid(false).build();

        assertThat(PdfSignatureInspector.overallOf(List.of(ok))).isEqualTo(Overall.ALL_VALID);
        assertThat(PdfSignatureInspector.overallOf(List.of(ok, unknown))).isEqualTo(Overall.HAS_UNVERIFIED);
        assertThat(PdfSignatureInspector.overallOf(List.of(ok, bad))).isEqualTo(Overall.HAS_INVALID);
        assertThat(PdfSignatureInspector.overallOf(List.of(noTime))).isEqualTo(Overall.HAS_UNVERIFIED);
        assertThat(PdfSignatureInspector.overallOf(List.of(selfSigned))).isEqualTo(Overall.HAS_UNVERIFIED);
    }

    private static int indexOf(byte[] data, byte[] needle) {
        outer:
        for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
