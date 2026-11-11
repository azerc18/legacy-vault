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

    @Test
    void fromAddress_unknown_isEmpty() {
        assertThat(VnProvince.fromAddress("Somewhere else")).isEmpty();
        assertThat(VnProvince.fromAddress(null)).isEmpty();
    }
}
