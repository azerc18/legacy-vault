package com.ltld.app.legacyvault.utility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShamirSecretSharingTest {

    private final SecureRandom random = new SecureRandom();

    private byte[] secret() {
        byte[] s = new byte[32];
        random.nextBytes(s);
        return s;
    }

    @Test
    @DisplayName("Đủ k phần (mọi tổ hợp) → ghép lại đúng bí mật")
    void anyKShares_recoverSecret() {
        byte[] s = secret();
        List<ShamirSecretSharing.Share> all = ShamirSecretSharing.split(s, 5, 3, random);
        for (int a = 0; a < 5; a++) {
            for (int b = a + 1; b < 5; b++) {
                for (int c = b + 1; c < 5; c++) {
                    assertArrayEquals(s, ShamirSecretSharing.combine(List.of(all.get(a), all.get(b), all.get(c))));
                }
            }
        }
    }

    @Test
    @DisplayName("Nhiều hơn k phần vẫn ghép đúng, thứ tự không quan trọng")
    void moreThanK_stillRecovers() {
        byte[] s = secret();
        List<ShamirSecretSharing.Share> all = new ArrayList<>(ShamirSecretSharing.split(s, 4, 2, random));
        Collections.shuffle(all, random);
        assertArrayEquals(s, ShamirSecretSharing.combine(all));
    }

    @Test
    @DisplayName("Ít hơn k phần → ra kết quả khác bí mật")
    void fewerThanK_doesNotRecover() {
        byte[] s = secret();
        List<ShamirSecretSharing.Share> all = ShamirSecretSharing.split(s, 5, 3, random);
        byte[] wrong = ShamirSecretSharing.combine(List.of(all.get(0), all.get(1)));
        assertFalse(java.util.Arrays.equals(s, wrong));
    }

    @Test
    @DisplayName("encode/decode giữ nguyên phần chia")
    void encodeDecode_roundTrip() {
        byte[] s = secret();
        List<ShamirSecretSharing.Share> all = ShamirSecretSharing.split(s, 3, 2, random);
        List<ShamirSecretSharing.Share> decoded = all.stream()
                .map(sh -> ShamirSecretSharing.Share.decode(sh.encode())).toList();
        assertEquals(all.get(1).x(), decoded.get(1).x());
        assertArrayEquals(s, ShamirSecretSharing.combine(decoded.subList(0, 2)));
    }

    @Test
    @DisplayName("Tham số sai hoặc phần bị lặp → IllegalArgumentException")
    void invalidArguments_throw() {
        byte[] s = secret();
        assertThrows(IllegalArgumentException.class, () -> ShamirSecretSharing.split(s, 3, 1, random));
        assertThrows(IllegalArgumentException.class, () -> ShamirSecretSharing.split(s, 2, 3, random));
        assertThrows(IllegalArgumentException.class, () -> ShamirSecretSharing.split(new byte[0], 3, 2, random));
        List<ShamirSecretSharing.Share> all = ShamirSecretSharing.split(s, 3, 2, random);
        assertThrows(IllegalArgumentException.class,
                () -> ShamirSecretSharing.combine(List.of(all.get(0), all.get(0))));
    }
}
