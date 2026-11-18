package com.ltld.app.legacyvault.service.legalverificationservice;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfDate;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfSignature;
import com.lowagie.text.pdf.PdfSignatureAppearance;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfString;
import com.lowagie.text.pdf.PdfWriter;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.cms.IssuerAndSerialNumber;
import org.bouncycastle.asn1.cms.SignedData;
import org.bouncycastle.asn1.cms.SignerIdentifier;
import org.bouncycastle.asn1.cms.SignerInfo;
import org.bouncycastle.asn1.cms.Time;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.TBSCertificate;
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * V2 (test): tạo PDF có chữ ký PAdES (adbe.pkcs7.detached) thật, chỉ bằng OpenPDF + các lớp ASN.1 của bcprov.
 * Chứng thư tự ký, khóa RSA 2048, SHA-256, có signedAttrs (contentType, messageDigest, signingTime).
 */
final class SignedPdfFixture {

    static final String SIGNER_CN = "Nguyen Van Cong Chung";
    static final String SIGNER_O = "UBND Phuong Ben Nghe";
    static final String CA_CN = "LegacyVault Test CA";
    private static final int CONTENTS_SIZE = 8192;

    private SignedPdfFixture() {
    }

    static byte[] unsignedPdf(String text) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document();
        PdfWriter.getInstance(doc, out);
        doc.open();
        doc.add(new Paragraph(text));
        doc.close();
        return out.toByteArray();
    }

    /** Ký PDF bằng chứng thư tự ký; notBefore/notAfter tính theo số ngày so với lúc ký. */
    static byte[] sign(byte[] pdf, int notBeforeDays, int notAfterDays) throws Exception {
        return sign(pdf, notBeforeDays, notAfterDays, false);
    }

    /** caIssued = true: chứng thư người ký do một "CA" riêng cấp (không tự ký), như chữ ký VNPT-CA thật. */
    static byte[] sign(byte[] pdf, int notBeforeDays, int notAfterDays, boolean caIssued) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        long now = System.currentTimeMillis();
        Date from = new Date(now + notBeforeDays * 86_400_000L);
        Date to = new Date(now + notAfterDays * 86_400_000L);
        X500Name subject = new X500Name("CN=" + SIGNER_CN + ", O=" + SIGNER_O + ", C=VN");
        Certificate cert;
        if (caIssued) {
            KeyPair ca = kpg.generateKeyPair();
            cert = issue(kp, subject, new X500Name("CN=" + CA_CN + ", O=Test CA, C=VN"), ca, from, to);
        } else {
            cert = issue(kp, subject, subject, kp, from, to);
        }

        PdfReader reader = new PdfReader(pdf);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfStamper stamper = PdfStamper.createSignature(reader, out, '\0');
        PdfSignatureAppearance app = stamper.getSignatureAppearance();
        app.setVisibleSignature(new Rectangle(36, 36, 200, 80), 1, "CertSignature");
        app.setReason("Chung thuc ban sao");
        app.setLayer2Text("Signed by " + SIGNER_CN); // có chữ sẵn thì OpenPDF không cần chuỗi chứng thư để vẽ khung
        Calendar cal = Calendar.getInstance();
        app.setSignDate(cal);
        PdfSignature dic = new PdfSignature(PdfName.ADOBE_PPKLITE, new PdfName("adbe.pkcs7.detached"));
        dic.setReason("Chung thuc ban sao");
        dic.setLocation("TP.HCM");
        dic.setDate(new PdfDate(cal));
        app.setCryptoDictionary(dic);
        Map<PdfName, Integer> exc = new HashMap<>();
        exc.put(PdfName.CONTENTS, CONTENTS_SIZE * 2 + 2);
        app.preClose(exc);

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = app.getRangeStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        byte[] cms = cms(kp, cert, md.digest(), cal.getTime());
        byte[] padded = new byte[CONTENTS_SIZE];
        System.arraycopy(cms, 0, padded, 0, cms.length);
        PdfDictionary update = new PdfDictionary();
        update.put(PdfName.CONTENTS, new PdfString(padded).setHexWriting(true));
        app.close(update);
        return out.toByteArray();
    }

    private static Certificate issue(KeyPair subjectKp, X500Name subject, X500Name issuer, KeyPair issuerKp,
                                     Date from, Date to) throws Exception {
        AlgorithmIdentifier alg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption, DERNull.INSTANCE);
        V3TBSCertificateGenerator gen = new V3TBSCertificateGenerator();
        gen.setSerialNumber(new ASN1Integer(BigInteger.valueOf(System.nanoTime())));
        gen.setSignature(alg);
        gen.setIssuer(issuer);
        gen.setSubject(subject);
        gen.setStartDate(new org.bouncycastle.asn1.x509.Time(from));
        gen.setEndDate(new org.bouncycastle.asn1.x509.Time(to));
        gen.setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(subjectKp.getPublic().getEncoded()));
        TBSCertificate tbs = gen.generateTBSCertificate();
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(issuerKp.getPrivate());
        s.update(tbs.getEncoded(ASN1Encoding.DER));
        return Certificate.getInstance(new DERSequence(new ASN1Encodable[]{tbs, alg, new DERBitString(s.sign())}));
    }

    private static byte[] cms(KeyPair kp, Certificate cert, byte[] digest, Date signingTime) throws Exception {
        ASN1EncodableVector attrs = new ASN1EncodableVector();
        attrs.add(new Attribute(PKCSObjectIdentifiers.pkcs_9_at_contentType, new DERSet(CMSObjectIdentifiers.data)));
        attrs.add(new Attribute(PKCSObjectIdentifiers.pkcs_9_at_messageDigest, new DERSet(new DEROctetString(digest))));
        attrs.add(new Attribute(PKCSObjectIdentifiers.pkcs_9_at_signingTime, new DERSet(new Time(signingTime))));
        DERSet signedAttrs = new DERSet(attrs);
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(kp.getPrivate());
        s.update(signedAttrs.getEncoded(ASN1Encoding.DER));
        AlgorithmIdentifier sha256 = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DERNull.INSTANCE);
        SignerInfo si = new SignerInfo(
                new SignerIdentifier(new IssuerAndSerialNumber(cert.getIssuer(), cert.getSerialNumber().getValue())),
                sha256, signedAttrs,
                new AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, DERNull.INSTANCE),
                new DEROctetString(s.sign()), null);
        SignedData sd = new SignedData(new DERSet(sha256), new ContentInfo(CMSObjectIdentifiers.data, null),
                new DERSet(cert), null, new DERSet(si));
        return new ContentInfo(CMSObjectIdentifiers.signedData, sd).getEncoded(ASN1Encoding.DER);
    }
}
