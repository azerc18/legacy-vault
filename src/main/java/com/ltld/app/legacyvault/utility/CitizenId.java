package com.ltld.app.legacyvault.utility;

import java.time.LocalDate;
import java.util.Set;

/**
 * Số định danh cá nhân / CCCD 12 chữ số.
 *
 * <p>Cấu trúc (Thông tư 59/2021/TT-BCA): 3 số mã tỉnh nơi đăng ký khai sinh, 1 số mã giới tính + thế kỷ sinh,
 * 2 số cuối năm sinh, 6 số ngẫu nhiên. Mã tỉnh là nơi đăng ký khai sinh, KHÔNG phải nơi ở hiện tại.
 */
public record CitizenId(String value) {

    public enum Gender { MALE, FEMALE }

    // Mã tỉnh nơi đăng ký khai sinh (63 tỉnh cũ). Số đã cấp không đổi khi sáp nhập tỉnh năm 2025.
    static final Set<String> BIRTH_PROVINCE_CODES = Set.of(
            "001", "002", "004", "006", "008", "010", "011", "012", "014", "015", "017", "019", "020", "022",
            "024", "025", "026", "027", "030", "031", "033", "034", "035", "036", "037", "038", "040", "042",
            "044", "045", "046", "048", "049", "051", "052", "054", "056", "058", "060", "062", "064", "066",
            "067", "068", "070", "072", "074", "075", "077", "079", "080", "082", "083", "084", "086", "087",
            "089", "091", "092", "093", "094", "095", "096");

    public CitizenId {
        if (value == null || !value.matches("^\\d{12}$")) {
            throw new IllegalArgumentException("Citizen ID must have exactly 12 digits");
        }
    }

    /** Bỏ khoảng trắng rồi kiểm định dạng 12 số và mã tỉnh. */
    public static CitizenId parse(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\s", "");
        if (!digits.matches("^\\d{12}$")) {
            throw new IllegalArgumentException("Citizen ID must have exactly 12 digits");
        }
        if (!BIRTH_PROVINCE_CODES.contains(digits.substring(0, 3))) {
            throw new IllegalArgumentException("The first 3 digits are not a valid province code");
        }
        return new CitizenId(digits);
    }

    /** CMND cũ: 9 chữ số. */
    public static boolean isValidOldId(String raw) {
        return raw != null && raw.replaceAll("\\s", "").matches("^\\d{9}$");
    }

    public Gender gender() {
        return (genderCenturyDigit() % 2 == 0) ? Gender.MALE : Gender.FEMALE;
    }

    /** Thế kỷ sinh: 0/1 → 1900, 2/3 → 2000, 4/5 → 2100, 6/7 → 2200, 8/9 → 1800. */
    public int birthCentury() {
        return switch (genderCenturyDigit() / 2) {
            case 0 -> 1900;
            case 1 -> 2000;
            case 2 -> 2100;
            case 3 -> 2200;
            default -> 1800;
        };
    }

    public int birthYear() {
        return birthCentury() + Integer.parseInt(value.substring(4, 6));
    }

    /** Năm sinh mã hoá trong số có khớp ngày sinh không. */
    public boolean matchesBirthDate(LocalDate dateOfBirth) {
        return dateOfBirth != null && dateOfBirth.getYear() == birthYear();
    }

    public boolean matchesGender(Gender g) {
        return g != null && g == gender();
    }

    /** 0790••••1234: dạng che bớt để hiển thị. */
    public String masked() {
        return value.substring(0, 4) + "••••" + value.substring(8);
    }

    private int genderCenturyDigit() {
        return value.charAt(3) - '0';
    }
}
