package com.ltld.app.legacyvault.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class VnProvinceTest {

    @Test
    void has34ProvincesAfter2025Merger() {
        assertThat(VnProvince.values()).hasSize(34);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            // Tỉnh cũ in trên CCCD trước 7/2025 -> tỉnh mới
            "Xã Tân Phú, Huyện Đồng Phú, Tỉnh Bình Phước; DONG_NAI",
            "Phường 1, TP Vũng Tàu, Tỉnh Bà Rịa - Vũng Tàu; HO_CHI_MINH",
            "Thị xã Thuận An, Bình Dương; HO_CHI_MINH",
            "Thành phố Huế, Tỉnh Thừa Thiên Huế; HUE",
            "Xã Hòa Bình, Huyện Tây Sơn, Tỉnh Bình Định; GIA_LAI",
            "Phường Hoà Bình, Thành phố Hoà Bình, Tỉnh Hoà Bình; PHU_THO",
            "Huyện Châu Thành, Tỉnh Kiên Giang; AN_GIANG",
            "Quận Hoàn Kiếm, Hà Nội; HA_NOI",
            "TP Buôn Ma Thuột, Đăk Lăk; DAK_LAK"
    })
    void fromAddress_mapsFormerProvinceToNewOne(String address, VnProvince expected) {
        assertThat(VnProvince.fromAddress(address)).contains(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            // 3 số đầu CCCD (nơi đăng ký khai sinh, bộ 63 mã cũ) -> tỉnh mới
            "074203123456; HO_CHI_MINH",   // Bình Dương
            "077203123456; HO_CHI_MINH",   // Bà Rịa - Vũng Tàu
            "079203123456; HO_CHI_MINH",
            "082195654321; DONG_THAP",     // Tiền Giang
            "049203123456; DA_NANG",       // Quảng Nam
            "062203123456; QUANG_NGAI",    // Kon Tum
            "095203123456; CA_MAU",        // Bạc Liêu
            "001203123456; HA_NOI",
            "079 203 123 456; HO_CHI_MINH" // có dấu cách
    })
    void fromCitizenIdPrefix_mapsBirthCodeToNewProvince(String citizenId, VnProvince expected) {
        assertThat(VnProvince.fromCitizenIdPrefix(citizenId)).contains(expected);
    }

    @Test
    void fromCitizenIdPrefix_coversExactlyTheValidCitizenIdCodes() {
        // Mã nào CitizenId.parse chấp nhận thì phải có tỉnh gợi ý, và ngược lại
        for (int code = 0; code <= 999; code++) {
            String id = String.format("%03d", code) + "203123456";
            boolean valid;
            try {
                com.ltld.app.legacyvault.utility.CitizenId.parse(id);
                valid = true;
            } catch (IllegalArgumentException e) {
                valid = false;
            }
            assertThat(VnProvince.fromCitizenIdPrefix(id).isPresent()).as("mã %03d", code).isEqualTo(valid);
        }
    }

    @Test
    void fromCitizenIdPrefix_invalidInput_isEmpty() {
        assertThat(VnProvince.fromCitizenIdPrefix(null)).isEmpty();
        assertThat(VnProvince.fromCitizenIdPrefix("07920312345")).isEmpty();    // 11 số
        assertThat(VnProvince.fromCitizenIdPrefix("07920312345a")).isEmpty();
        assertThat(VnProvince.fromCitizenIdPrefix("003203123456")).isEmpty();   // không phải mã tỉnh
    }

    @Test
    void fromAddress_unknown_isEmpty() {
        assertThat(VnProvince.fromAddress("Somewhere else")).isEmpty();
        assertThat(VnProvince.fromAddress(null)).isEmpty();
    }
}
