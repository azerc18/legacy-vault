package com.ltld.app.legacyvault.utility;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Số di động Việt Nam đã chuẩn hoá. Dùng chung cho hồ sơ, xác minh danh tính (KYC) và xác minh ví điện tử.
 *
 * <p>Đầu vào chấp nhận: {@code 0912345678}, {@code +84912345678}, {@code 84912345678}, {@code +84 0912 345 678},
 * có thể kèm khoảng trắng, dấu chấm, gạch ngang, ngoặc. Chỉ nhận số di động 10 chữ số (từ 2018 không còn số 11 chữ số).
 *
 * <p>Lưu DB dạng E.164 ({@link #e164()}), hiển thị dạng {@link #display()}.
 */
public record VnPhoneNumber(String national) {

    /** Lý do số không hợp lệ; FE dựa vào đây để hiện câu báo lỗi cụ thể. */
    public enum Problem {
        EMPTY, INVALID_CHARACTERS, FOREIGN, LANDLINE, LEGACY_11_DIGIT, INVALID_LENGTH, INVALID_PREFIX
    }

    public static final class InvalidVnPhoneException extends IllegalArgumentException {
        private final Problem problem;
        private final String suggestion;

        InvalidVnPhoneException(Problem problem, String message, String suggestion) {
            super(message);
            this.problem = problem;
            this.suggestion = suggestion;
        }

        public Problem problem() { return problem; }

        /** Số 10 chữ số gợi ý khi người dùng nhập số 11 chữ số cũ; null nếu không có. */
        public String suggestion() { return suggestion; }
    }

    // Đầu số di động 3 chữ số (gồm số 0) đang cấp. Nhà mạng mở đầu số mới thì chỉ sửa ở đây.
    static final Set<String> MOBILE_PREFIXES = Set.of(
            "032", "033", "034", "035", "036", "037", "038", "039",
            "052", "055", "056", "058", "059",
            "070", "076", "077", "078", "079",
            "081", "082", "083", "084", "085", "086", "087", "088", "089",
            "090", "091", "092", "093", "094", "096", "097", "098", "099");

    // Bảng chuyển đầu số 11 chữ số cũ sang 10 chữ số (đợt chuyển đổi 2018)
    private static final Map<String, String> LEGACY_PREFIX_MAP = Map.ofEntries(
            Map.entry("0162", "032"), Map.entry("0163", "033"), Map.entry("0164", "034"), Map.entry("0165", "035"),
            Map.entry("0166", "036"), Map.entry("0167", "037"), Map.entry("0168", "038"), Map.entry("0169", "039"),
            Map.entry("0120", "070"), Map.entry("0121", "079"), Map.entry("0122", "077"), Map.entry("0126", "076"),
            Map.entry("0128", "078"),
            Map.entry("0123", "083"), Map.entry("0124", "084"), Map.entry("0125", "085"), Map.entry("0127", "081"),
            Map.entry("0129", "082"),
            Map.entry("0186", "056"), Map.entry("0188", "058"), Map.entry("0199", "059"));

    private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-()]");
    private static final Pattern ALLOWED = Pattern.compile("^\\+?\\d+$");

    public VnPhoneNumber {
        if (national == null || !national.matches("^0\\d{9}$")) {
            throw new IllegalArgumentException("national must be a 10-digit number starting with 0");
        }
    }

    /**
     * Chuẩn hoá và kiểm tra.
     *
     * @throws InvalidVnPhoneException khi không phải số di động Việt Nam hợp lệ
     */
    public static VnPhoneNumber parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidVnPhoneException(Problem.EMPTY, "Phone number is required", null);
        }
        String compact = SEPARATORS.matcher(raw.trim()).replaceAll("");
        if (!ALLOWED.matcher(compact).matches()) {
            throw new InvalidVnPhoneException(Problem.INVALID_CHARACTERS,
                    "Phone number may only contain digits, spaces, dots, dashes, brackets and a leading +", null);
        }

        String national;
        if (compact.startsWith("+")) {
            if (!compact.startsWith("+84")) {
                throw foreign();
            }
            national = toNational(compact.substring(3));
        } else if (compact.startsWith("84") && (compact.length() == 11 || compact.length() == 12)) {
            // 84xxxxxxxxx (quên dấu +) hoặc 840xxxxxxxxx
            national = toNational(compact.substring(2));
        } else if (compact.startsWith("0")) {
            national = compact;
        } else if (compact.length() == 9) {
            // Gõ thiếu số 0 đầu: 912345678
            national = "0" + compact;
        } else {
            throw foreign();
        }

        if (national.startsWith("02")) {
            throw new InvalidVnPhoneException(Problem.LANDLINE,
                    "Landline numbers cannot receive SMS codes; please use a mobile number", null);
        }
        if (national.length() == 11 && national.startsWith("01")) {
            String newPrefix = LEGACY_PREFIX_MAP.get(national.substring(0, 4));
            String suggestion = newPrefix == null ? null : newPrefix + national.substring(4);
            throw new InvalidVnPhoneException(Problem.LEGACY_11_DIGIT,
                    "11-digit mobile numbers were converted to 10 digits in 2018"
                            + (suggestion == null ? "" : "; try " + suggestion), suggestion);
        }
        if (national.length() != 10) {
            throw new InvalidVnPhoneException(Problem.INVALID_LENGTH,
                    "Vietnamese mobile numbers have 10 digits (e.g. 0912 345 678)", null);
        }
        if (!MOBILE_PREFIXES.contains(national.substring(0, 3))) {
            throw new InvalidVnPhoneException(Problem.INVALID_PREFIX,
                    "Unknown Vietnamese mobile prefix " + national.substring(0, 3), null);
        }
        return new VnPhoneNumber(national);
    }

    /** Như {@link #parse} nhưng trả rỗng thay vì ném lỗi. */
    public static Optional<VnPhoneNumber> tryParse(String raw) {
        try {
            return Optional.of(parse(raw));
        } catch (InvalidVnPhoneException e) {
            return Optional.empty();
        }
    }

    /** +84912345678: dạng lưu DB. */
    public String e164() {
        return "+84" + national.substring(1);
    }

    /** 0912 345 678: dạng hiển thị. */
    public String display() {
        return national.substring(0, 4) + " " + national.substring(4, 7) + " " + national.substring(7);
    }

    /** 0912 ••• 678: dạng che bớt. */
    public String masked() {
        return national.substring(0, 4) + " ••• " + national.substring(7);
    }

    public String carrier() {
        return switch (national.substring(0, 3)) {
            case "086", "096", "097", "098", "032", "033", "034", "035", "036", "037", "038", "039" -> "Viettel";
            case "088", "091", "094", "081", "082", "083", "084", "085" -> "VinaPhone";
            case "089", "090", "093", "070", "076", "077", "078", "079" -> "MobiFone";
            case "092", "052", "056", "058" -> "Vietnamobile";
            case "099", "059" -> "Gmobile";
            case "087" -> "iTel";
            case "055" -> "Reddi";
            default -> "Vietnam Mobile";
        };
    }

    private static String toNational(String afterCountryCode) {
        // +84 0912... : bỏ số 0 thừa sau mã quốc gia
        return afterCountryCode.startsWith("0") ? afterCountryCode : "0" + afterCountryCode;
    }

    private static InvalidVnPhoneException foreign() {
        return new InvalidVnPhoneException(Problem.FOREIGN, "Only Vietnamese mobile numbers are supported", null);
    }
}
