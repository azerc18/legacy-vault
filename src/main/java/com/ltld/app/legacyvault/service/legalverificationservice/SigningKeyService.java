package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.entity.SigningKey;
import com.ltld.app.legacyvault.repository.SigningKeyRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * V3: quản lý cặp khóa ECDSA P-256 của hệ thống dùng để ký biên bản xác nhận của Legal Verifier.
 * <ul>
 *   <li>Lần ký đầu tiên tự sinh khóa (không cần thêm biến môi trường trên Azure).</li>
 *   <li>Khóa riêng lưu dạng PKCS#8 Base64 đã mã hóa bằng {@link CryptoService} (CRYPTO_MASTER_KEY).</li>
 *   <li>Khóa công khai lưu PEM (X.509 SubjectPublicKeyInfo) để bất kỳ ai cũng kiểm lại được bằng OpenSSL.</li>
 * </ul>
 * Đây là chữ ký do LegacyVault cấp, KHÔNG phải chữ ký số có chứng thư của tổ chức CA được cấp phép.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SigningKeyService {

    public static final String KEY_ALGORITHM = "EC";
    public static final String CURVE = "secp256r1";
    public static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    static final String FIRST_KEY_REF = "lv-sign-v1";

    private final SigningKeyRepository repository;
    private final CryptoService cryptoService;
    private final PlatformTransactionManager transactionManager;

    /** Khóa đang dùng để ký; chưa có thì sinh mới (transaction riêng để không bị rollback cùng lệnh ký). */
    public SigningKey getOrCreateActiveKey() {
        return repository.findFirstByActiveTrueOrderByCreatedAtDesc().orElseGet(this::createFirstKey);
    }

       private SigningKey createFirstKey() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            return tx.execute(status -> repository.saveAndFlush(generate(FIRST_KEY_REF)));
        } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
            // Hai lệnh ký đầu tiên chạy cùng lúc: MySQL báo trùng key_ref (unique) HOẶC deadlock khi chèn cùng lúc.
            // Bên thua đọc lại khóa bên thắng trong transaction MỚI (review #293 m1); bên thắng có thể chưa commit
            // ngay nên thử lại vài lần.
            log.info("Signing key {} was created concurrently ({}), reloading", FIRST_KEY_REF, e.getClass().getSimpleName());
            TransactionTemplate reload = new TransactionTemplate(transactionManager);
            reload.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            reload.setReadOnly(true);
            for (int attempt = 0; attempt < 5; attempt++) {
                SigningKey winner = reload.execute(status -> repository.findByKeyRef(FIRST_KEY_REF).orElse(null));
                if (winner != null) {
                    return winner;
                }
                try {
                    Thread.sleep(50L * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            throw new IllegalStateException("Signing key could not be created", e);
        }
    }

    SigningKey generate(String keyRef) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            generator.initialize(new ECGenParameterSpec(CURVE), new SecureRandom());
            KeyPair pair = generator.generateKeyPair();
            byte[] publicDer = pair.getPublic().getEncoded();
            return SigningKey.builder()
                    .keyRef(keyRef)
                    .algorithm(SIGNATURE_ALGORITHM)
                    .publicKeyPem(toPem(publicDer))
                    .privateKeyEncrypted(cryptoService.encrypt(
                            Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())))
                    .fingerprintSha256(sha256Hex(publicDer))
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate signing key", e);
        }
    }

    /** Ký chuỗi UTF-8, trả chữ ký DER dạng Base64. */
    public String sign(SigningKey key, String payload) {
        try {
            byte[] pkcs8 = Base64.getDecoder().decode(cryptoService.decrypt(key.getPrivateKeyEncrypted()));
            PrivateKey privateKey = KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
            Signature signer = Signature.getInstance(SIGNATURE_ALGORITHM);
            signer.initSign(privateKey);
            signer.update(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign the verification seal", e);
        }
    }

    /** Kiểm chữ ký bằng khóa công khai PEM. Dữ liệu hỏng (Base64/PEM sai) coi là chữ ký không hợp lệ. */
    public boolean verify(String publicKeyPem, String payload, String signatureBase64) {
        try {
            PublicKey publicKey = KeyFactory.getInstance(KEY_ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(fromPem(publicKeyPem)));
            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    static String toPem(byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n";
    }

    static byte[] fromPem(String pem) {
        String body = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
