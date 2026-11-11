package com.ltld.app.legacyvault.enums;

import com.ltld.app.legacyvault.utility.VnText;

import java.util.List;
import java.util.Optional;

/**
 * 34 tỉnh, thành phố sau sắp xếp đơn vị hành chính (hiệu lực 01/07/2025).
 * {@code formerNames} là các tỉnh cũ đã nhập vào, dùng để suy ra tỉnh mới từ địa chỉ in trên CCCD cấp trước 7/2025.
 */
public enum VnProvince {
    HA_NOI("Hà Nội", "Hà Nội"),
    HUE("Huế", "Thừa Thiên Huế", "Huế"),
    LAI_CHAU("Lai Châu", "Lai Châu"),
    DIEN_BIEN("Điện Biên", "Điện Biên"),
    SON_LA("Sơn La", "Sơn La"),
    LANG_SON("Lạng Sơn", "Lạng Sơn"),
    QUANG_NINH("Quảng Ninh", "Quảng Ninh"),
    THANH_HOA("Thanh Hóa", "Thanh Hóa"),
    NGHE_AN("Nghệ An", "Nghệ An"),
    HA_TINH("Hà Tĩnh", "Hà Tĩnh"),
    CAO_BANG("Cao Bằng", "Cao Bằng"),
    TUYEN_QUANG("Tuyên Quang", "Tuyên Quang", "Hà Giang"),
    LAO_CAI("Lào Cai", "Lào Cai", "Yên Bái"),
    THAI_NGUYEN("Thái Nguyên", "Thái Nguyên", "Bắc Kạn"),
    PHU_THO("Phú Thọ", "Phú Thọ", "Vĩnh Phúc", "Hòa Bình"),
    BAC_NINH("Bắc Ninh", "Bắc Ninh", "Bắc Giang"),
    HUNG_YEN("Hưng Yên", "Hưng Yên", "Thái Bình"),
    HAI_PHONG("Hải Phòng", "Hải Phòng", "Hải Dương"),
    NINH_BINH("Ninh Bình", "Ninh Bình", "Hà Nam", "Nam Định"),
    QUANG_TRI("Quảng Trị", "Quảng Trị", "Quảng Bình"),
    DA_NANG("Đà Nẵng", "Đà Nẵng", "Quảng Nam"),
    QUANG_NGAI("Quảng Ngãi", "Quảng Ngãi", "Kon Tum"),
    GIA_LAI("Gia Lai", "Gia Lai", "Bình Định"),
    KHANH_HOA("Khánh Hòa", "Khánh Hòa", "Ninh Thuận"),
    LAM_DONG("Lâm Đồng", "Lâm Đồng", "Đắk Nông", "Bình Thuận"),
    DAK_LAK("Đắk Lắk", "Đắk Lắk", "Phú Yên"),
    HO_CHI_MINH("TP. Hồ Chí Minh", "Hồ Chí Minh", "Bình Dương", "Bà Rịa Vũng Tàu", "Bà Rịa - Vũng Tàu"),
    DONG_NAI("Đồng Nai", "Đồng Nai", "Bình Phước"),
    TAY_NINH("Tây Ninh", "Tây Ninh", "Long An"),
    CAN_THO("Cần Thơ", "Cần Thơ", "Sóc Trăng", "Hậu Giang"),
    VINH_LONG("Vĩnh Long", "Vĩnh Long", "Bến Tre", "Trà Vinh"),
    DONG_THAP("Đồng Tháp", "Đồng Tháp", "Tiền Giang"),
    CA_MAU("Cà Mau", "Cà Mau", "Bạc Liêu"),
    AN_GIANG("An Giang", "An Giang", "Kiên Giang");

    private final String displayName;
    private final List<String> foldedNames;

    VnProvince(String displayName, String... names) {
        this.displayName = displayName;
        this.foldedNames = java.util.Arrays.stream(names).map(VnText::fold).toList();
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Suy ra tỉnh (mới) từ chuỗi địa chỉ. Tên tỉnh thường nằm cuối địa chỉ, nên chọn tên khớp kết thúc muộn nhất;
     * hoà thì lấy tên dài hơn.
     */
    public static Optional<VnProvince> fromAddress(String address) {
        String folded = VnText.fold(address);
        if (folded.isEmpty()) {
            return Optional.empty();
        }
        VnProvince best = null;
        int bestEnd = -1;
        int bestLen = -1;
        for (VnProvince p : values()) {
            for (String name : p.foldedNames) {
                int idx = folded.lastIndexOf(name);
                if (idx < 0 || !isWordBoundary(folded, idx, name.length())) continue;
                int end = idx + name.length();
                if (end > bestEnd || (end == bestEnd && name.length() > bestLen)) {
                    best = p;
                    bestEnd = end;
                    bestLen = name.length();
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean isWordBoundary(String s, int start, int len) {
        boolean before = start == 0 || !Character.isLetterOrDigit(s.charAt(start - 1));
        int end = start + len;
        boolean after = end >= s.length() || !Character.isLetterOrDigit(s.charAt(end));
        return before && after;
    }
}
