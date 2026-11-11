package com.ltld.app.legacyvault.utility;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Đọc chuỗi trong mã QR mặt trước CCCD gắn chip:
 * {@code SốCCCD|SốCMNDcũ|Họ tên|ngàysinh ddMMyyyy|Giới tính|Địa chỉ thường trú|ngàycấp ddMMyyyy}.
 * Số CMND cũ có thể rỗng. Một số thẻ thêm trường ở cuối, nên chỉ đọc 7 trường đầu.
 */
public final class CitizenQrParser {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("ddMMuuuu");
    private static final int MAX_LENGTH = 1024;

    public record Result(String citizenId, String oldIdNumber, String fullName, LocalDate dateOfBirth,
                         CitizenId.Gender gender, String address, LocalDate issueDate) {
    }

    private CitizenQrParser() {
    }

    /** @throws IllegalArgumentException khi chuỗi không đúng định dạng QR CCCD */
    public static Result parse(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("QR content is empty or too long");
        }
        String[] parts = raw.trim().split("\\|", -1);
        if (parts.length < 7) {
            throw new IllegalArgumentException("This is not a citizen ID card QR code");
        }
        String citizenId = parts[0].trim();
        if (!citizenId.matches("^\\d{12}$")) {
            throw new IllegalArgumentException("This is not a citizen ID card QR code");
        }
        String oldId = parts[1].trim();
        String name = parts[2].trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("The QR code has no full name");
        }
        return new Result(
                citizenId,
                oldId.isEmpty() ? null : oldId,
                VnText.titleCaseName(name),
                parseDate(parts[3]),
                parseGender(parts[4]),
                parts[5].trim().isEmpty() ? null : parts[5].trim(),
                parseDate(parts[6]));
    }

    public static CitizenId.Gender parseGender(String raw) {
        String g = VnText.fold(raw);
        return switch (g) {
            case "NAM", "MALE", "M" -> CitizenId.Gender.MALE;
            case "NU", "FEMALE", "F" -> CitizenId.Gender.FEMALE;
            default -> null;
        };
    }

    private static LocalDate parseDate(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(s, DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date in QR code: " + s);
        }
    }
}
