package com.ltld.app.legacyvault.utility;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CitizenIdTest {

    @Test
    void parse_decodesGenderAndBirthYear() {
        // 079 = TP.HCM, 2 = nam sinh thế kỷ 21, 01 = năm 2001
        CitizenId id = CitizenId.parse("079 201 123456");
        assertThat(id.value()).isEqualTo("079201123456");
        assertThat(id.gender()).isEqualTo(CitizenId.Gender.MALE);
        assertThat(id.birthYear()).isEqualTo(2001);
        assertThat(id.matchesBirthDate(LocalDate.of(2001, 5, 2))).isTrue();
        assertThat(id.matchesBirthDate(LocalDate.of(1901, 5, 2))).isFalse();
        assertThat(id.masked()).isEqualTo("0792••••3456");
    }

    @Test
    void parse_femaleBornIn1900s() {
        CitizenId id = CitizenId.parse("001185000001");
        assertThat(id.gender()).isEqualTo(CitizenId.Gender.FEMALE);
        assertThat(id.birthYear()).isEqualTo(1985);
    }

    @Test
    void parse_rejectsWrongLengthOrProvinceCode() {
        assertThatThrownBy(() -> CitizenId.parse("07920112345")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CitizenId.parse("07920112345a")).isInstanceOf(IllegalArgumentException.class);
        // 003 không phải mã tỉnh
        assertThatThrownBy(() -> CitizenId.parse("003201123456"))
                .hasMessageContaining("province code");
    }

    @Test
    void oldId_mustHave9Digits() {
        assertThat(CitizenId.isValidOldId("025 123 456")).isTrue();
        assertThat(CitizenId.isValidOldId("12345678")).isFalse();
        assertThat(CitizenId.isValidOldId(null)).isFalse();
    }
}
