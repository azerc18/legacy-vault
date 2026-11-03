package com.ltld.app.legacyvault.utility;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * X1: Shamir's Secret Sharing trên trường GF(256) (đa thức AES 0x11b), xử lý từng byte của bí mật.
 * Chia bí mật thành n phần, bất kỳ k phần nào cũng ghép lại được, ít hơn k phần không suy ra được gì.
 */
public final class ShamirSecretSharing {

    private static final int[] EXP = new int[512];
    private static final int[] LOG = new int[256];

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            LOG[x] = i;
            int x2 = x << 1;
            if ((x2 & 0x100) != 0) {
                x2 ^= 0x11b;
            }
            x ^= x2;                 // nhân với phần tử sinh 3
        }
        for (int i = 255; i < 512; i++) {
            EXP[i] = EXP[i - 255];
        }
    }

    private ShamirSecretSharing() {
    }

    /** Một phần của bí mật: x là toạ độ 1..255, y có cùng độ dài với bí mật. */
    public record Share(int x, byte[] y) {

        /** Dạng chuỗi để lưu DB: "x:base64(y)". */
        public String encode() {
            return x + ":" + Base64.getEncoder().encodeToString(y);
        }

        public static Share decode(String encoded) {
            int sep = encoded == null ? -1 : encoded.indexOf(':');
            if (sep <= 0) {
                throw new IllegalArgumentException("Invalid share format");
            }
            int x = Integer.parseInt(encoded.substring(0, sep));
            return new Share(x, Base64.getDecoder().decode(encoded.substring(sep + 1)));
        }
    }

    /** Chia secret thành n phần, cần k phần để ghép lại (2 <= k <= n <= 255). */
    public static List<Share> split(byte[] secret, int n, int k, SecureRandom random) {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("Secret must not be empty");
        }
        if (k < 2 || k > n || n > 255) {
            throw new IllegalArgumentException("Need 2 <= k <= n <= 255");
        }
        byte[][] ys = new byte[n][secret.length];
        byte[] coef = new byte[k];
        for (int pos = 0; pos < secret.length; pos++) {
            random.nextBytes(coef);
            coef[0] = secret[pos];
            for (int i = 0; i < n; i++) {
                ys[i][pos] = (byte) evaluate(coef, i + 1);
            }
        }
        List<Share> shares = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            shares.add(new Share(i + 1, ys[i]));
        }
        return shares;
    }

    /** Ghép lại bí mật bằng nội suy Lagrange tại 0. Nếu đưa ít hơn k phần, kết quả sai (không báo lỗi). */
    public static byte[] combine(List<Share> shares) {
        if (shares == null || shares.isEmpty()) {
            throw new IllegalArgumentException("No shares given");
        }
        int len = shares.get(0).y().length;
        Set<Integer> xs = new HashSet<>();
        for (Share s : shares) {
            if (s.x() < 1 || s.x() > 255 || !xs.add(s.x()) || s.y().length != len) {
                throw new IllegalArgumentException("Shares are invalid or duplicated");
            }
        }
        byte[] secret = new byte[len];
        for (int pos = 0; pos < len; pos++) {
            int acc = 0;
            for (Share si : shares) {
                int num = 1;
                int den = 1;
                for (Share sj : shares) {
                    if (sj != si) {
                        num = mul(num, sj.x());
                        den = mul(den, sj.x() ^ si.x());
                    }
                }
                acc ^= mul(si.y()[pos] & 0xff, div(num, den));
            }
            secret[pos] = (byte) acc;
        }
        return secret;
    }

    private static int evaluate(byte[] coef, int x) {
        int result = 0;
        for (int i = coef.length - 1; i >= 0; i--) {   // Horner
            result = mul(result, x) ^ (coef[i] & 0xff);
        }
        return result;
    }

    private static int mul(int a, int b) {
        return (a == 0 || b == 0) ? 0 : EXP[LOG[a] + LOG[b]];
    }

    private static int div(int a, int b) {
        if (b == 0) {
            throw new ArithmeticException("Division by zero in GF(256)");
        }
        return a == 0 ? 0 : EXP[LOG[a] + 255 - LOG[b]];
    }
}
