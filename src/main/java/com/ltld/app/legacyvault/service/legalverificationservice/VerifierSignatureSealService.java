package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.dto.legalverificationdto.VerifierSignatureDto;
import com.ltld.app.legacyvault.entity.DigitalSignature;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.SigningKey;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.SignatureSealStatus;
import com.ltld.app.legacyvault.exception.StorageUnavailableException;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * V3: "con dấu" kiểm lại được cho chữ ký xác nhận của Legal Verifier.
 *
 * <p>Lúc ký: tính SHA-256 của giấy chứng tử (bản đã giải mã), dựng một chuỗi chuẩn hóa (payload) gồm
 * id hồ sơ, verifier, owner, executor, phương thức xác thực, mã băm tài liệu, danh sách vault, thời điểm,
 * rồi ký payload bằng khóa ECDSA P-256 của hệ thống. Payload lưu nguyên văn, nên kiểm lại không phụ thuộc
 * cách dựng chuỗi ở phiên bản code sau.</p>
 *
 * <p>Lúc kiểm: (1) chữ ký đúng với khóa công khai, (2) payload nói đúng hồ sơ này và đúng verifier,
 * (3) file giấy chứng tử đang lưu vẫn có cùng mã băm. Đủ 3 điều mới là VALID.</p>
 *
 * <p>Giới hạn: khóa do LegacyVault giữ (mã hóa bằng CRYPTO_MASTER_KEY), không phải chứng thư số của CA.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerifierSignatureSealService {

    static final String PAYLOAD_HEADER = "LEGACYVAULT-VERIFIER-SEAL v1";

    private final SigningKeyService signingKeyService;
    private final FileStorageService fileStorageService;
    private final CryptoService cryptoService;

    /**
     * Gắn con dấu vào chữ ký vừa tạo (chưa lưu). Lỗi đọc file hay lỗi ký sẽ ném ra, làm cả lệnh ký rollback:
     * thà không mở khóa Vault còn hơn mở khóa mà không có bằng chứng kiểm lại được.
     */
    public void seal(DigitalSignature signature, LegalVerificationRequest request,
                     String verifierEmail, Collection<UUID> vaultIds) {
        seal(signature, request, verifierEmail, vaultIds, null);
    }

    /**
     * Như trên, kèm ảnh chữ ký tay (PNG đã kiểm) khi Verifier ký tay: mã băm ảnh vào payload,
     * ảnh mã hóa bằng CRYPTO_MASTER_KEY lưu cùng chữ ký. {@code signatureImagePng == null} với OTP.
     */
    public void seal(DigitalSignature signature, LegalVerificationRequest request,
                     String verifierEmail, Collection<UUID> vaultIds, byte[] signatureImagePng) {
        String documentSha256 = SigningKeyService.sha256Hex(readDocument(request));
        SigningKey key = signingKeyService.getOrCreateActiveKey();

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("request_id", String.valueOf(request.getId()));
        fields.put("verifier_id", String.valueOf(signature.getVerifier().getId()));
        fields.put("verifier_email", verifierEmail == null ? "" : verifierEmail);
        fields.put("owner_id", request.getOwner() == null ? "" : String.valueOf(request.getOwner().getId()));
        fields.put("executor_id", request.getExecutor() == null ? "" : String.valueOf(request.getExecutor().getId()));
        fields.put("method", String.valueOf(signature.getSignatureMethod()));
        fields.put("document_sha256", documentSha256);
        fields.put("vault_ids", vaultIds.stream().map(String::valueOf).sorted().collect(Collectors.joining(",")));
        fields.put("issued_at", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        fields.put("key_ref", key.getKeyRef());
        String imageSha256 = null;
        if (signatureImagePng != null) {
            imageSha256 = SigningKeyService.sha256Hex(signatureImagePng);
            fields.put("signature_image_sha256", imageSha256);
            signature.setSignatureImageSha256(imageSha256);
            signature.setSignatureImageEncrypted(encryptImage(signatureImagePng));
        }
        String payload = buildPayload(fields);

        signature.setDocumentSha256(documentSha256);
        signature.setSignedPayload(payload);
        signature.setSignatureValue(signingKeyService.sign(key, payload));
        signature.setSignatureAlgorithm(SigningKeyService.SIGNATURE_ALGORITHM);
        signature.setSigningKey(key);
        // Mã băm cũ giờ gắn với nội dung thật (hồ sơ thừa kế PDF đang in trường này)
        signature.setSignatureHash(SigningKeyService.sha256Hex(payload.getBytes(StandardCharsets.UTF_8)));
    }

    /** Kiểm lại chữ ký ngay lúc gọi. Không ghi gì vào DB. */
    public VerifierSignatureDto check(DigitalSignature signature) {
        LegalVerificationRequest request = signature.getRequest();
        User verifier = signature.getVerifier();
        VerifierSignatureDto.VerifierSignatureDtoBuilder dto = VerifierSignatureDto.builder()
                .requestId(request.getId())
                .signatureId(signature.getId())
                .method(signature.getSignatureMethod())
                .signedAt(signature.getSignedAt())
                .verifierName(verifier.getFullName())
                .verifierEmail(verifier.getEmail())
                .signatureHash(signature.getSignatureHash());

        SigningKey key = signature.getSigningKey();
        if (signature.getSignedPayload() == null || signature.getSignatureValue() == null || key == null) {
            return dto.status(SignatureSealStatus.LEGACY).build();
        }

        String payload = signature.getSignedPayload();
        boolean signatureValid = signingKeyService.verify(key.getPublicKeyPem(), payload, signature.getSignatureValue());
        Map<String, String> fields = parsePayload(payload);
        boolean payloadMatches = String.valueOf(request.getId()).equals(fields.get("request_id"))
                && String.valueOf(verifier.getId()).equals(fields.get("verifier_id"))
                && signature.getDocumentSha256() != null
                && signature.getDocumentSha256().equals(fields.get("document_sha256"))
                && key.getKeyRef().equals(fields.get("key_ref"))
                // Ký tay: mã băm ảnh phải nằm trong payload; ký OTP: payload không được có dòng này
                && Objects.equals(signature.getSignatureImageSha256(), fields.get("signature_image_sha256"));

        // Ký tay: ảnh đang lưu phải còn đúng mã băm lúc ký (null = không có ảnh, không kiểm)
        Boolean imageMatches = null;
        if (signature.getSignatureImageSha256() != null) {
            byte[] png = decryptImage(signature.getSignatureImageEncrypted());
            imageMatches = png != null && signature.getSignatureImageSha256().equals(SigningKeyService.sha256Hex(png));
        }

        String currentSha = null;
        try {
            currentSha = SigningKeyService.sha256Hex(readDocument(request));
        } catch (RuntimeException e) {
            log.warn("V3: could not re-read death certificate of request {} to re-check the seal", request.getId(), e);
        }
        Boolean documentMatches = currentSha == null ? null : currentSha.equals(signature.getDocumentSha256());

        SignatureSealStatus status;
        if (!signatureValid || !payloadMatches || Boolean.FALSE.equals(documentMatches)
                || Boolean.FALSE.equals(imageMatches)) {
            status = SignatureSealStatus.INVALID;
        } else if (documentMatches == null) {
            status = SignatureSealStatus.DOCUMENT_UNAVAILABLE;
        } else {
            status = SignatureSealStatus.VALID;
        }

        return dto.status(status)
                .signatureValid(signatureValid)
                .payloadMatches(payloadMatches)
                .documentMatches(documentMatches)
                .hasSignatureImage(signature.getSignatureImageSha256() != null)
                .signatureImageMatches(imageMatches)
                .signatureImageSha256(signature.getSignatureImageSha256())
                .documentSha256(signature.getDocumentSha256())
                .currentDocumentSha256(currentSha)
                .algorithm(signature.getSignatureAlgorithm())
                .keyRef(key.getKeyRef())
                .keyFingerprint(key.getFingerprintSha256())
                .publicKeyPem(key.getPublicKeyPem())
                .signedPayload(payload)
                .signatureValue(signature.getSignatureValue())
                .build();
    }

    // Mỗi dòng "khóa=giá trị", xuống dòng bằng \n, không có dòng trống cuối
    static String buildPayload(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder(PAYLOAD_HEADER);
        fields.forEach((k, v) -> sb.append('\n').append(k).append('=').append(v.replace("\n", " ")));
        return sb.toString();
    }

    static Map<String, String> parsePayload(String payload) {
        Map<String, String> out = new LinkedHashMap<>();
        String[] lines = payload.split("\n");
        if (lines.length == 0 || !PAYLOAD_HEADER.equals(lines[0])) {
            return out;
        }
        for (int i = 1; i < lines.length; i++) {
            int eq = lines[i].indexOf('=');
            if (eq > 0) {
                out.put(lines[i].substring(0, eq), lines[i].substring(eq + 1));
            }
        }
        return out;
    }

    /** Ảnh chữ ký tay (PNG) đã giải mã; null nếu không có hoặc giải mã lỗi. Không ném lỗi. */
    public byte[] decryptImage(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(cryptoService.decrypt(encrypted));
        } catch (Exception e) {
            log.warn("V3: could not decrypt a handwritten signature image", e);
            return null;
        }
    }

    private String encryptImage(byte[] png) {
        try {
            return cryptoService.encrypt(Base64.getEncoder().encodeToString(png));
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt the signature image.", e);
        }
    }

    private byte[] readDocument(LegalVerificationRequest request) {
        if (request.getDeathCertificateFileUrlEncrypted() == null) {
            throw new IllegalStateException("The request has no death certificate to seal.");
        }
        try {
            return cryptoService.decryptBytes(fileStorageService.readFile(request.getDeathCertificateFileUrlEncrypted()));
        } catch (StorageUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the death certificate.", e);
        }
    }
}
