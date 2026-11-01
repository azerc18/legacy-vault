package com.ltld.app.legacyvault.dto.vaultdto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// N2-owner (m2): kiểm hợp lệ sharePercent ở lớp DTO (tối đa 2 chữ số thập phân, 0.01 – 100)
class DigitalAssetRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void init() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static DigitalAssetRequest.Recipient recipient(String percent) {
        DigitalAssetRequest.Recipient r = new DigitalAssetRequest.Recipient();
        r.setBeneficiaryId(UUID.randomUUID());
        r.setSharePercent(percent == null ? null : new BigDecimal(percent));
        return r;
    }

    @Test
    void sharePercent_nullAllowed_forExclusive() {
        assertThat(validator.validate(recipient(null))).isEmpty();
    }

    @Test
    void sharePercent_twoDecimals_valid() {
        assertThat(validator.validate(recipient("33.33"))).isEmpty();
        assertThat(validator.validate(recipient("100.00"))).isEmpty();
    }

    @Test
    void sharePercent_threeDecimals_invalid() {
        assertThat(validator.validate(recipient("33.333"))).isNotEmpty();
    }

    @Test
    void sharePercent_above100_invalid() {
        assertThat(validator.validate(recipient("100.01"))).isNotEmpty();
        assertThat(validator.validate(recipient("101"))).isNotEmpty();
    }

    @Test
    void sharePercent_belowMinimum_invalid() {
        assertThat(validator.validate(recipient("0"))).isNotEmpty();
        assertThat(validator.validate(recipient("-5"))).isNotEmpty();
    }
}