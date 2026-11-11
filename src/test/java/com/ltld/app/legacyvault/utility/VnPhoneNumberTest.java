package com.ltld.app.legacyvault.utility;

import com.ltld.app.legacyvault.utility.VnPhoneNumber.InvalidVnPhoneException;
import com.ltld.app.legacyvault.utility.VnPhoneNumber.Problem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VnPhoneNumberTest {

    @ParameterizedTest
    @CsvSource({
            "0912345678, +84912345678",
            "+84912345678, +84912345678",
            "84912345678, +84912345678",
            "+84 0912 345 678, +84912345678",
            "840912345678, +84912345678",
            "0912.345.678, +84912345678",
            "'(091) 234-5678', +84912345678",
            "912345678, +84912345678",
            "0386123456, +84386123456",
            "0589123456, +84589123456",
            "0702123456, +84702123456",
            "0879123456, +84879123456"
    })
    void parse_acceptsCommonVietnameseFormats(String input, String e164) {
        assertThat(VnPhoneNumber.parse(input).e164()).isEqualTo(e164);
    }

    @Test
    void formats_forDisplayAndMasking() {
        VnPhoneNumber p = VnPhoneNumber.parse("+84912345678");
        assertThat(p.national()).isEqualTo("0912345678");
        assertThat(p.display()).isEqualTo("0912 345 678");
        assertThat(p.masked()).isEqualTo("0912 ••• 678");
        assertThat(p.carrier()).isEqualTo("VinaPhone");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void parse_empty(String input) {
        assertProblem(input, Problem.EMPTY);
    }

    @Test
    void parse_legacy11Digit_suggestsNewNumber() {
        assertThatThrownBy(() -> VnPhoneNumber.parse("01681234567"))
                .isInstanceOfSatisfying(InvalidVnPhoneException.class, e -> {
                    assertThat(e.problem()).isEqualTo(Problem.LEGACY_11_DIGIT);
                    assertThat(e.suggestion()).isEqualTo("0381234567");
                });
    }

    @Test
    void parse_landline_rejected() {
        assertProblem("02838123456", Problem.LANDLINE);
        assertProblem("+84 24 3823 4567", Problem.LANDLINE);
    }

    @Test
    void parse_wrongLength_rejected() {
        assertProblem("091234567", Problem.INVALID_LENGTH);
        assertProblem("09123456789", Problem.INVALID_LENGTH);
    }

    @Test
    void parse_unknownPrefix_rejected() {
        assertProblem("0112345678", Problem.INVALID_PREFIX);
        assertProblem("0951234567", Problem.INVALID_PREFIX);
    }

    @Test
    void parse_foreignNumber_rejected() {
        assertProblem("+1 415 555 0100", Problem.FOREIGN);
        assertProblem("+65 9123 4567", Problem.FOREIGN);
    }

    @Test
    void parse_lettersOrSymbols_rejected() {
        assertProblem("09123abc78", Problem.INVALID_CHARACTERS);
        assertProblem("0912#345678", Problem.INVALID_CHARACTERS);
    }

    @Test
    void tryParse_returnsEmptyInsteadOfThrowing() {
        assertThat(VnPhoneNumber.tryParse("abc")).isEmpty();
        assertThat(VnPhoneNumber.tryParse("0912345678")).isPresent();
    }

    private static void assertProblem(String input, Problem expected) {
        assertThatThrownBy(() -> VnPhoneNumber.parse(input))
                .isInstanceOfSatisfying(InvalidVnPhoneException.class,
                        e -> assertThat(e.problem()).isEqualTo(expected));
    }
}
