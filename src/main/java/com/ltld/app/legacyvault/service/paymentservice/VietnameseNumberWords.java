package com.ltld.app.legacyvault.service.paymentservice;

/**
 * Đọc số tiền thành chữ tiếng Việt cho dòng "Số tiền viết bằng chữ" của hoá đơn,
 * ví dụ 99000 -> "Chín mươi chín nghìn đồng chẵn."
 */
final class VietnameseNumberWords {

    private static final String[] DIGITS = {"không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"};
    private static final String[] UNITS = {"", "nghìn", "triệu", "tỷ", "nghìn tỷ"};

    private VietnameseNumberWords() {
    }

    /** Số tiền đồng (>= 0) thành câu hoàn chỉnh, viết hoa chữ đầu, kết thúc bằng "đồng chẵn.". */
    static String vndInWords(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0");
        }
        String words = amount == 0 ? "không" : numberInWords(amount);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1) + " đồng chẵn.";
    }

    static String numberInWords(long n) {
        if (n >= 1_000_000_000_000_000L) {
            throw new IllegalArgumentException("amount too large");
        }
        // Tách thành các nhóm 3 chữ số, nhóm thấp nhất ở vị trí 0
        int[] groups = new int[UNITS.length];
        long rest = n;
        for (int i = 0; i < groups.length; i++) {
            groups[i] = (int) (rest % 1000);
            rest /= 1000;
        }
        StringBuilder sb = new StringBuilder();
        boolean started = false;
        for (int i = groups.length - 1; i >= 0; i--) {
            if (groups[i] == 0) {
                continue;
            }
            appendWord(sb, readGroup(groups[i], started));
            appendWord(sb, UNITS[i]);
            started = true;
        }
        return sb.toString();
    }

    /** Đọc một nhóm 3 chữ số; withLeadingZeroHundred = đọc "không trăm" khi đã có nhóm cao hơn. */
    private static String readGroup(int g, boolean withLeadingZeroHundred) {
        int hundreds = g / 100;
        int tens = (g / 10) % 10;
        int ones = g % 10;
        StringBuilder sb = new StringBuilder();
        if (hundreds > 0 || withLeadingZeroHundred) {
            appendWord(sb, DIGITS[hundreds]);
            appendWord(sb, "trăm");
        }
        if (tens == 0) {
            if (ones > 0) {
                if (hundreds > 0 || withLeadingZeroHundred) {
                    appendWord(sb, "linh");
                }
                appendWord(sb, DIGITS[ones]);
            }
        } else {
            appendWord(sb, tens == 1 ? "mười" : DIGITS[tens] + " mươi");
            if (ones > 0) {
                String one;
                if (ones == 1 && tens > 1) {
                    one = "mốt";
                } else if (ones == 5) {
                    one = "lăm";
                } else if (ones == 4 && tens > 1) {
                    one = "tư";
                } else {
                    one = DIGITS[ones];
                }
                appendWord(sb, one);
            }
        }
        return sb.toString();
    }

    private static void appendWord(StringBuilder sb, String word) {
        if (word.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(word);
    }
}
