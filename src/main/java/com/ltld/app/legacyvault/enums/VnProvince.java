package com.ltld.app.legacyvault.enums;

import com.ltld.app.legacyvault.utility.VnText;

import java.util.List;
import java.util.Map;
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

    /**
     * 3 số đầu CCCD (nơi đăng ký khai sinh, bộ 63 mã trước sắp xếp) -> tỉnh mới. Chỉ dùng để GỢI Ý khi không đọc được
     * tỉnh từ địa chỉ: nơi khai sinh có thể khác nơi đang ở, nên không được dùng thay cho địa chỉ.
     */
    private static final Map<String, VnProvince> BY_BIRTH_CODE = Map.ofEntries(
            Map.entry("001", HA_NOI), Map.entry("002", TUYEN_QUANG),
            Map.entry("004", CAO_BANG), Map.entry("006", THAI_NGUYEN),
            Map.entry("008", TUYEN_QUANG), Map.entry("010", LAO_CAI),
            Map.entry("011", DIEN_BIEN), Map.entry("012", LAI_CHAU),
            Map.entry("014", SON_LA), Map.entry("015", LAO_CAI),
            Map.entry("017", PHU_THO), Map.entry("019", THAI_NGUYEN),
            Map.entry("020", LANG_SON), Map.entry("022", QUANG_NINH),
            Map.entry("024", BAC_NINH), Map.entry("025", PHU_THO),
            Map.entry("026", PHU_THO), Map.entry("027", BAC_NINH),
            Map.entry("030", HAI_PHONG), Map.entry("031", HAI_PHONG),
            Map.entry("033", HUNG_YEN), Map.entry("034", HUNG_YEN),
            Map.entry("035", NINH_BINH), Map.entry("036", NINH_BINH),
            Map.entry("037", NINH_BINH), Map.entry("038", THANH_HOA),
            Map.entry("040", NGHE_AN), Map.entry("042", HA_TINH),
            Map.entry("044", QUANG_TRI), Map.entry("045", QUANG_TRI),
            Map.entry("046", HUE), Map.entry("048", DA_NANG),
            Map.entry("049", DA_NANG), Map.entry("051", QUANG_NGAI),
            Map.entry("052", GIA_LAI), Map.entry("054", DAK_LAK),
            Map.entry("056", KHANH_HOA), Map.entry("058", KHANH_HOA),
            Map.entry("060", LAM_DONG), Map.entry("062", QUANG_NGAI),
            Map.entry("064", GIA_LAI), Map.entry("066", DAK_LAK),
            Map.entry("067", LAM_DONG), Map.entry("068", LAM_DONG),
            Map.entry("070", DONG_NAI), Map.entry("072", TAY_NINH),
            Map.entry("074", HO_CHI_MINH), Map.entry("075", DONG_NAI),
            Map.entry("077", HO_CHI_MINH), Map.entry("079", HO_CHI_MINH),
            Map.entry("080", TAY_NINH), Map.entry("082", DONG_THAP),
            Map.entry("083", VINH_LONG), Map.entry("084", VINH_LONG),
            Map.entry("086", VINH_LONG), Map.entry("087", DONG_THAP),
            Map.entry("089", AN_GIANG), Map.entry("091", AN_GIANG),
            Map.entry("092", CAN_THO), Map.entry("093", CAN_THO),
            Map.entry("094", CAN_THO), Map.entry("095", CA_MAU),
            Map.entry("096", CA_MAU));

    /** Tỉnh mới theo 3 số đầu của số CCCD 12 chữ số; rỗng nếu số không hợp lệ hoặc mã không có trong bảng. */
    public static Optional<VnProvince> fromCitizenIdPrefix(String citizenId) {
        if (citizenId == null) {
            return Optional.empty();
        }
        String digits = citizenId.replaceAll("\\s", "");
        if (!digits.matches("\\d{12}")) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_BIRTH_CODE.get(digits.substring(0, 3)));
    }

    private static boolean isWordBoundary(String s, int start, int len) {
        boolean before = start == 0 || !Character.isLetterOrDigit(s.charAt(start - 1));
        int end = start + len;
        boolean after = end >= s.length() || !Character.isLetterOrDigit(s.charAt(end));
        return before && after;
    }
}
