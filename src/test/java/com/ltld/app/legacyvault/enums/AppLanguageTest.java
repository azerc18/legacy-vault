package com.ltld.app.legacyvault.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AppLanguageTest {

    @ParameterizedTest
    @CsvSource({"vi,VI", "VI,VI", "vi-VN,VI", "vi_VN,VI", "en,EN", "EN,EN", "en-US,EN", " en ,EN"})
    void fromTag_readsSupportedLanguages(String raw, AppLanguage expected) {
        assertThat(AppLanguage.fromTag(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "fr", "xx-YY", "123"})
    void fromTag_fallsBackToVietnamese(String raw) {
        assertThat(AppLanguage.fromTag(raw)).isEqualTo(AppLanguage.VI);
    }

    @Test
    void tagAndLocale() {
        assertThat(AppLanguage.EN.tag()).isEqualTo("en");
        assertThat(AppLanguage.VI.toLocale().getLanguage()).isEqualTo("vi");
        assertThat(AppLanguage.DEFAULT).isEqualTo(AppLanguage.VI);
    }
}
