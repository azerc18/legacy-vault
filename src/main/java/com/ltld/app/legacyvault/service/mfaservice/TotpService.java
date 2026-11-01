package com.ltld.app.legacyvault.service.mfaservice;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.util.Utils;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;

@Component
public class TotpService {
    private static final int PERIOD = 30;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();

    public String newSecret() {
        return secretGenerator.generate();
    }

    public String otpauthUri(String secret, String email) {
        return qrData(secret, email).getUri();
    }

    public String qrDataUri(String secret, String email) {
        try {
            return Utils.getDataUriForImage(qrGenerator.generate(qrData(secret, email)), qrGenerator.getImageMimeType());
        } catch (QrGenerationException e) {
            throw new IllegalStateException("Cannot generate QR", e);
        }
    }

    /**
     * Kiểm tra mã trong cửa sổ ±1 bước (lệch đồng hồ tối đa 30s).
     * Trả về bước thời gian khớp, hoặc empty nếu sai / đã dùng (step <= lastUsedStep).
     */
    public OptionalLong verify(String secret, String code, Long lastUsedStep) {
        if (code == null || !code.matches("\\d{6}")) return OptionalLong.empty();
        long current = Instant.now().getEpochSecond() / PERIOD;
        for (long step = current - 1; step <= current + 1; step++) {
            if (lastUsedStep != null && step <= lastUsedStep) continue;
            try {
                String expected = codeGenerator.generate(secret, step);
                if (MessageDigest.isEqual(expected.getBytes(), code.getBytes())) return OptionalLong.of(step);
            } catch (CodeGenerationException e) {
                throw new IllegalStateException(e);
            }
        }
        return OptionalLong.empty();
    }

    private QrData qrData(String secret, String email) {
        return new QrData.Builder().label(email).secret(secret).issuer("LegacyVault")
                .algorithm(HashingAlgorithm.SHA1).digits(6).period(PERIOD).build();
    }
}
