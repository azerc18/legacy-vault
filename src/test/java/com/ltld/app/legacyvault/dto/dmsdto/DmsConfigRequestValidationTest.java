package com.ltld.app.legacyvault.dto.dmsdto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DmsConfigRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    @Test
    @DisplayName("FR-03: request hợp lệ (kể cả giá trị biên) -> không có lỗi")
    void validRequest_hasNoViolations() {
        assertThat(validator.validate(request(30, 7, 3))).isEmpty();
        assertThat(validator.validate(request(1, 1, 1))).isEmpty();
        assertThat(validator.validate(request(365, 90, 10))).isEmpty();
    }

    @Test
    @DisplayName("FR-03: thiếu field -> báo lỗi đúng từng field")
    void nullFields_areRejected() {
        Set<ConstraintViolation<DmsConfigRequest>> violations = validator.validate(new DmsConfigRequest());

        assertThat(violations).extracting(v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage)
                .containsExactlyInAnyOrder(
                        tuple("checkinIntervalDays", "Chu kỳ check-in không được để trống"),
                        tuple("gracePeriodDays", "Thời gian chờ không được để trống"),
                        tuple("reminderCount", "Số lần nhắc không được để trống"));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
            "checkinIntervalDays, 0, Chu kỳ check-in phải từ 1 ngày trở lên",
            "checkinIntervalDays, -1, Chu kỳ check-in phải từ 1 ngày trở lên",
            "checkinIntervalDays, 366, Chu kỳ check-in tối đa 365 ngày",
            "gracePeriodDays, 0, Thời gian chờ phải từ 1 ngày trở lên",
            "gracePeriodDays, -7, Thời gian chờ phải từ 1 ngày trở lên",
            "gracePeriodDays, 91, Thời gian chờ tối đa 90 ngày",
            "reminderCount, 0, Số lần nhắc phải từ 1 trở lên",
            "reminderCount, -3, Số lần nhắc phải từ 1 trở lên",
            "reminderCount, 11, Số lần nhắc tối đa 10"
    })
    @DisplayName("FR-03: giá trị 0, âm hoặc vượt giới hạn -> lỗi đúng field và message")
    void outOfRangeValue_isRejected(String field, int value, String expectedMessage) {
        DmsConfigRequest request = request(30, 7, 3);
        switch (field) {
            case "checkinIntervalDays" -> request.setCheckinIntervalDays(value);
            case "gracePeriodDays" -> request.setGracePeriodDays(value);
            case "reminderCount" -> request.setReminderCount(value);
            default -> throw new IllegalArgumentException(field);
        }

        Set<ConstraintViolation<DmsConfigRequest>> violations = validator.validate(request);

        assertThat(violations).singleElement().satisfies(v -> {
            assertThat(v.getPropertyPath().toString()).isEqualTo(field);
            assertThat(v.getMessage()).isEqualTo(expectedMessage);
        });
    }

    private static DmsConfigRequest request(Integer intervalDays, Integer gracePeriodDays, Integer reminderCount) {
        DmsConfigRequest request = new DmsConfigRequest();
        request.setCheckinIntervalDays(intervalDays);
        request.setGracePeriodDays(gracePeriodDays);
        request.setReminderCount(reminderCount);
        return request;
    }
}
