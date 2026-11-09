package com.ltld.app.legacyvault.service.assetverificationservice;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * C.3.3: bảng độ dài số tài khoản theo ngân hàng, đọc từ app.verification.bank.account-lengths.
 * Khóa là tên ngân hàng viết thường, không dấu, không khoảng trắng (vd vietcombank). Dữ liệu do nhóm
 * kiểm tra từ nguồn chính thức của ngân hàng rồi điền vào yaml; KHÔNG điền theo trí nhớ.
 * Ngân hàng chưa có trong bảng thì không kiểm được (BANK_FORMAT_CHECK từ chối).
 */
@Component
@ConfigurationProperties("app.verification.bank")
public class BankAccountRules {

    private Map<String, List<Integer>> accountLengths = new HashMap<>();

    public Map<String, List<Integer>> getAccountLengths() {
        return accountLengths;
    }

    public void setAccountLengths(Map<String, List<Integer>> accountLengths) {
        this.accountLengths = accountLengths == null ? new HashMap<>() : accountLengths;
    }

    /** Các độ dài hợp lệ của ngân hàng (so khóa đã chuẩn hóa), rỗng nếu ngân hàng chưa có trong bảng. */
    public Optional<List<Integer>> allowedLengths(String institutionName) {
        String key = keyOf(institutionName);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        for (Map.Entry<String, List<Integer>> e : accountLengths.entrySet()) {
            if (keyOf(e.getKey()).equals(key) && e.getValue() != null && !e.getValue().isEmpty()) {
                return Optional.of(List.copyOf(e.getValue()));
            }
        }
        return Optional.empty();
    }

    /** Khóa ngân hàng: bỏ dấu, chữ thường, chỉ giữ chữ và số. */
    public static String keyOf(String institutionName) {
        if (institutionName == null) {
            return "";
        }
        return stripAccents(institutionName).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static String stripAccents(String input) {
        String s = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return s.replace('đ', 'd').replace('Đ', 'D');
    }

    /** Bảng đã có ít nhất một ngân hàng với danh sách độ dài hợp lệ chưa. */
    public boolean hasAnyRule() {
        return accountLengths.values().stream().anyMatch(v -> v != null && !v.isEmpty());
    }
}
