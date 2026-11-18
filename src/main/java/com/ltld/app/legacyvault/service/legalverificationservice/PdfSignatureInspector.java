package com.ltld.app.legacyvault.service.legalverificationservice;

import com.lowagie.text.pdf.AcroFields;
import com.lowagie.text.pdf.PdfArray;
import com.lowagie.text.pdf.PdfDate;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfNumber;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfString;
import com.ltld.app.legacyvault.config.AppTimeZone;
import com.ltld.app.legacyvault.dto.legalverificationdto.PdfSignatureReportDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.PdfSignatureReportDto.DocumentKind;
import com.ltld.app.legacyvault.dto.legalverificationdto.PdfSignatureReportDto.Overall;
import com.ltld.app.legacyvault.dto.legalverificationdto.PdfSignatureReportDto.SignatureInfo;
import com.ltld.app.legacyvault.service.fileservice.UploadedDocumentValidator;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.cms.IssuerAndSerialNumber;
import org.bouncycastle.asn1.cms.SignedData;
import org.bouncycastle.asn1.cms.SignerInfo;
import org.bouncycastle.asn1.cms.Time;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V2: đọc và kiểm chữ ký số nhúng trong PDF (chuẩn PAdES / PKCS#7 detached, loại VNPT-CA, Viettel-CA, BKAV-CA... dùng).
 *
 * <p>Chỉ dùng OpenPDF để TÌM chữ ký (trường chữ ký, /ByteRange, /Contents) và tự kiểm CMS bằng các lớp ASN.1 của
 * bcprov (đã có trong pom), không cần thêm thư viện bcpkix.</p>
 *
 * <p>Với mỗi chữ ký, kiểm:</p>
 * <ol>
 *   <li><b>Toàn vẹn</b>: băm đúng các đoạn trong /ByteRange, so với thuộc tính messageDigest, rồi kiểm chữ ký trên
 *       signedAttrs bằng khóa công khai của chứng thư người ký;</li>
 *   <li><b>Phủ toàn bộ file</b>: không có nội dung nào được thêm vào sau chữ ký;</li>
 *   <li><b>Chứng thư còn hạn lúc ký</b>, và có phải chứng thư tự ký không.</li>
 * </ol>
 *
 * <p>KHÔNG kiểm chuỗi chứng thư tới CA gốc, không tra CRL/OCSP: đó là việc của danh sách tin cậy quốc gia, chưa có.
 * Kết quả chỉ để Verifier tham khảo, không tự động duyệt/từ chối.</p>
 */
@Slf4j
@Component
public class PdfSignatureInspector {

    // Giờ Việt Nam cố định như phần còn lại của hệ thống (#291), không phụ thuộc múi giờ JVM lúc nạp class
    private static final ZoneId ZONE = AppTimeZone.VN;

    private static final Map<ASN1ObjectIdentifier, String> DIGESTS = Map.of(
            OIWObjectIdentifiers.idSHA1, "SHA-1",
            NISTObjectIdentifiers.id_sha224, "SHA-224",
            NISTObjectIdentifiers.id_sha256, "SHA-256",
            NISTObjectIdentifiers.id_sha384, "SHA-384",
            NISTObjectIdentifiers.id_sha512, "SHA-512");

    public PdfSignatureReportDto inspect(UUID requestId, byte[] file) {
        PdfSignatureReportDto.PdfSignatureReportDtoBuilder report = PdfSignatureReportDto.builder()
                .requestId(requestId)
                .signatures(List.of());
        String type = UploadedDocumentValidator.detectContentType(file);
        if (!"application/pdf".equals(type)) {
            // Ảnh chụp/scan không mang chữ ký số được
            boolean image = type.startsWith("image/");
            return report.documentKind(image ? DocumentKind.IMAGE : DocumentKind.OTHER)
                    .overall(Overall.NOT_PDF).build();
        }
        report.documentKind(DocumentKind.PDF);

        PdfReader reader;
        try {
            reader = new PdfReader(file);
        } catch (Exception e) {
            log.warn("V2: could not parse PDF of request {}", requestId, e);
            return report.overall(Overall.UNREADABLE).build();
        }
        try {
            AcroFields fields = reader.getAcroFields();
            List<String> names = fields.getSignedFieldNames();
            List<SignatureInfo> infos = new ArrayList<>();
            for (String name : names) {
                infos.add(inspectOne(fields, name, file));
            }
            return report.signatures(infos)
                    .signatureCount(infos.size())
                    .overall(overallOf(infos))
                    .build();
        } catch (RuntimeException e) {
            log.warn("V2: could not read signatures of request {}", requestId, e);
            return report.overall(Overall.UNREADABLE).build();
        } finally {
            reader.close();
        }
    }

    static Overall overallOf(List<SignatureInfo> infos) {
        if (infos.isEmpty()) {
            return Overall.NO_SIGNATURE;
        }
        if (infos.stream().anyMatch(i -> Boolean.FALSE.equals(i.getIntegrityValid()))) {
            return Overall.HAS_INVALID;
        }
        // Banner xanh chỉ khi MỌI điều kiện đã kiểm được và đạt (review #295 m1):
        // - "chưa kiểm được" thời điểm ký (null) không được coi là đạt;
        // - chứng thư tự ký thì ai cũng tạo được, nên luôn cần Verifier xem thêm.
        boolean allPassed = infos.stream().allMatch(i -> Boolean.TRUE.equals(i.getIntegrityValid())
                && Boolean.TRUE.equals(i.getCertificateValidAtSigning())
                && !Boolean.TRUE.equals(i.getSelfSigned()));
        // Phải có ít nhất 1 chữ ký phủ cả file, nếu không thì có nội dung thêm sau khi ký
        boolean someCoversAll = infos.stream().anyMatch(i -> Boolean.TRUE.equals(i.getCoversWholeDocument()));
        return allPassed && someCoversAll ? Overall.ALL_VALID : Overall.HAS_UNVERIFIED;
    }

    private SignatureInfo inspectOne(AcroFields fields, String name, byte[] file) {
        SignatureInfo.SignatureInfoBuilder info = SignatureInfo.builder().fieldName(name);
        PdfDictionary dict = fields.getSignatureDictionary(name);
        if (dict == null) {
            return info.problem("SIGNATURE_DICTIONARY_MISSING").build();
        }
        PdfName subFilter = dict.getAsName(PdfName.SUBFILTER);
        info.subFilter(subFilter == null ? null : PdfName.decodeName(subFilter.toString()));
        info.reason(text(dict.getAsString(PdfName.REASON)));
        info.location(text(dict.getAsString(PdfName.LOCATION)));
        String dictName = text(dict.getAsString(PdfName.NAME));
        LocalDateTime pdfTime = pdfDate(dict.getAsString(PdfName.M));
        try {
            info.coversWholeDocument(fields.signatureCoversWholeDocument(name));
        } catch (RuntimeException e) {
            info.coversWholeDocument(null);
        }

        try {
            long[] range = byteRange(dict.getAsArray(PdfName.BYTERANGE), file.length);
            PdfString contents = dict.getAsString(PdfName.CONTENTS);
            if (range == null || contents == null) {
                return info.signerName(dictName).signedAt(pdfTime).problem("BYTE_RANGE_INVALID")
                        .integrityValid(false).build();
            }
            verifyCms(contents.getOriginalBytes(), file, range, info);
        } catch (UnsupportedSignatureException e) {
            info.problem(e.getMessage());
        } catch (Exception e) {
            log.debug("V2: signature {} could not be verified", name, e);
            info.problem("SIGNATURE_DATA_CORRUPT").integrityValid(false);
        }

        SignatureInfo built = info.build();
        if (built.getSignerName() == null) {
            built.setSignerName(dictName);
        }
        if (built.getSignedAt() == null) {
            built.setSignedAt(pdfTime);
        }
        if (built.getCertificateValidAtSigning() == null && built.getSignedAt() != null
                && built.getCertificateNotBefore() != null && built.getCertificateNotAfter() != null) {
            built.setCertificateValidAtSigning(!built.getSignedAt().isBefore(built.getCertificateNotBefore())
                    && !built.getSignedAt().isAfter(built.getCertificateNotAfter()));
        }
        return built;
    }

    private void verifyCms(byte[] contents, byte[] file, long[] range, SignatureInfo.SignatureInfoBuilder info)
            throws Exception {
        ASN1Primitive obj;
        try (ASN1InputStream in = new ASN1InputStream(contents)) {
            obj = in.readObject(); // phần đệm 0x00 phía sau bị bỏ qua
        }
        ContentInfo ci = ContentInfo.getInstance(obj);
        if (!CMSObjectIdentifiers.signedData.equals(ci.getContentType())) {
            throw new UnsupportedSignatureException("NOT_CMS_SIGNED_DATA");
        }
        SignedData sd = SignedData.getInstance(ci.getContent());
        if (sd.getSignerInfos().size() == 0) {
            throw new UnsupportedSignatureException("NO_SIGNER");
        }
        SignerInfo si = SignerInfo.getInstance(sd.getSignerInfos().getObjectAt(0));

        X509Certificate cert = signerCertificate(sd, si);
        if (cert == null) {
            throw new UnsupportedSignatureException("SIGNER_CERTIFICATE_MISSING");
        }
        X500Name subject = X500Name.getInstance(cert.getSubjectX500Principal().getEncoded());
        X500Name issuer = X500Name.getInstance(cert.getIssuerX500Principal().getEncoded());
        info.signerName(rdn(subject, BCStyle.CN))
                .signerOrganization(rdn(subject, BCStyle.O))
                .issuer(rdn(issuer, BCStyle.CN) != null ? rdn(issuer, BCStyle.CN) : issuer.toString())
                .serialNumber(cert.getSerialNumber().toString(16).toUpperCase())
                .certificateNotBefore(toLocal(cert.getNotBefore()))
                .certificateNotAfter(toLocal(cert.getNotAfter()))
                .selfSigned(isSelfSigned(cert));

        String digestName = DIGESTS.get(si.getDigestAlgorithm().getAlgorithm());
        if (digestName == null) {
            throw new UnsupportedSignatureException("DIGEST_ALGORITHM_UNSUPPORTED");
        }
        info.digestAlgorithm(digestName);
        byte[] contentDigest = digestRanges(digestName, file, range);

        ASN1Set signedAttrs = si.getAuthenticatedAttributes();
        byte[] signedBytes;
        if (signedAttrs != null) {
            byte[] messageDigest = null;
            for (ASN1Encodable e : signedAttrs) {
                Attribute a = Attribute.getInstance(e);
                if (PKCSObjectIdentifiers.pkcs_9_at_messageDigest.equals(a.getAttrType())) {
                    messageDigest = ASN1OctetString.getInstance(a.getAttrValues().getObjectAt(0)).getOctets();
                } else if (PKCSObjectIdentifiers.pkcs_9_at_signingTime.equals(a.getAttrType())) {
                    info.signedAt(toLocal(Time.getInstance(a.getAttrValues().getObjectAt(0)).getDate()));
                }
            }
            if (messageDigest == null || !MessageDigest.isEqual(messageDigest, contentDigest)) {
                // Nội dung trong vùng đã ký bị sửa
                info.integrityValid(false);
                return;
            }
            signedBytes = signedAttrs.getEncoded(ASN1Encoding.DER);
        } else {
            // Không có signedAttrs: chữ ký ký thẳng lên dữ liệu
            signedBytes = readRanges(file, range);
        }

        String jcaAlg = signatureAlgorithm(digestName, si.getDigestEncryptionAlgorithm().getAlgorithm());
        Signature verifier = Signature.getInstance(jcaAlg);
        verifier.initVerify(cert.getPublicKey());
        verifier.update(signedBytes);
        info.integrityValid(verifier.verify(si.getEncryptedDigest().getOctets()));
    }

    private static X509Certificate signerCertificate(SignedData sd, SignerInfo si) throws Exception {
        if (sd.getCertificates() == null) {
            return null;
        }
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certs = new ArrayList<>();
        for (ASN1Encodable e : sd.getCertificates()) {
            certs.add((X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(e.toASN1Primitive().getEncoded(ASN1Encoding.DER))));
        }
        ASN1Encodable id = si.getSID().getId();
        if (!si.getSID().isTagged()) {
            IssuerAndSerialNumber ias = IssuerAndSerialNumber.getInstance(id);
            for (X509Certificate c : certs) {
                if (c.getSerialNumber().equals(ias.getSerialNumber().getValue())
                        && X500Name.getInstance(c.getIssuerX500Principal().getEncoded()).equals(ias.getName())) {
                    return c;
                }
            }
        } else {
            byte[] ski = ASN1OctetString.getInstance(id).getOctets();
            for (X509Certificate c : certs) {
                byte[] ext = c.getExtensionValue("2.5.29.14");
                if (ext != null) {
                    byte[] value = ASN1OctetString.getInstance(
                            ASN1OctetString.getInstance(ext).getOctets()).getOctets();
                    if (Arrays.equals(value, ski)) {
                        return c;
                    }
                }
            }
        }
        // Không khớp được định danh: chỉ chấp nhận khi chữ ký mang đúng 1 chứng thư
        return certs.size() == 1 ? certs.get(0) : null;
    }

    static String signatureAlgorithm(String digestName, ASN1ObjectIdentifier encAlg) {
        String d = digestName.replace("-", "");
        if (PKCSObjectIdentifiers.rsaEncryption.equals(encAlg)
                || PKCSObjectIdentifiers.sha1WithRSAEncryption.equals(encAlg)
                || PKCSObjectIdentifiers.sha256WithRSAEncryption.equals(encAlg)
                || PKCSObjectIdentifiers.sha384WithRSAEncryption.equals(encAlg)
                || PKCSObjectIdentifiers.sha512WithRSAEncryption.equals(encAlg)) {
            return d + "withRSA";
        }
        if (X9ObjectIdentifiers.id_ecPublicKey.equals(encAlg)
                || X9ObjectIdentifiers.ecdsa_with_SHA1.equals(encAlg)
                || X9ObjectIdentifiers.ecdsa_with_SHA256.equals(encAlg)
                || X9ObjectIdentifiers.ecdsa_with_SHA384.equals(encAlg)
                || X9ObjectIdentifiers.ecdsa_with_SHA512.equals(encAlg)) {
            return d + "withECDSA";
        }
        // RSASSA-PSS, GOST... chưa hỗ trợ
        throw new UnsupportedSignatureException("SIGNATURE_ALGORITHM_UNSUPPORTED");
    }

    static long[] byteRange(PdfArray array, long fileLength) {
        if (array == null || array.size() != 4) {
            return null;
        }
        long[] r = new long[4];
        for (int i = 0; i < 4; i++) {
            PdfNumber n = array.getAsNumber(i);
            if (n == null) {
                return null;
            }
            r[i] = (long) n.doubleValue();
        }
        // Đoạn 1 bắt đầu từ 0, đoạn 2 nằm sau đoạn 1, không vượt quá file
        if (r[0] != 0 || r[1] < 0 || r[2] < r[1] || r[3] < 0 || r[2] + r[3] > fileLength) {
            return null;
        }
        return r;
    }

    private static byte[] digestRanges(String digestName, byte[] file, long[] r) throws Exception {
        MessageDigest md = MessageDigest.getInstance(digestName);
        md.update(file, (int) r[0], (int) r[1]);
        md.update(file, (int) r[2], (int) r[3]);
        return md.digest();
    }

    private static byte[] readRanges(byte[] file, long[] r) {
        byte[] out = new byte[(int) (r[1] + r[3])];
        System.arraycopy(file, (int) r[0], out, 0, (int) r[1]);
        System.arraycopy(file, (int) r[2], out, (int) r[1], (int) r[3]);
        return out;
    }

    private static boolean isSelfSigned(X509Certificate cert) {
        if (!cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal())) {
            return false;
        }
        try {
            cert.verify(cert.getPublicKey());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String rdn(X500Name name, ASN1ObjectIdentifier type) {
        RDN[] rdns = name.getRDNs(type);
        return rdns.length == 0 ? null : IETFUtils.valueToString(rdns[0].getFirst().getValue());
    }

    private static String text(PdfString s) {
        return s == null ? null : s.toUnicodeString();
    }

    private static LocalDateTime pdfDate(PdfString s) {
        if (s == null) {
            return null;
        }
        try {
            Calendar c = PdfDate.decode(s.toString());
            return c == null ? null : toLocal(c.getTime());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LocalDateTime toLocal(Date d) {
        return d == null ? null : LocalDateTime.ofInstant(d.toInstant(), ZONE);
    }

    /** Chữ ký hợp chuẩn nhưng dùng thuật toán / cấu trúc chưa hỗ trợ: không kết luận được, không phải "sai". */
    static class UnsupportedSignatureException extends RuntimeException {
        UnsupportedSignatureException(String code) {
            super(code);
        }
    }
}
