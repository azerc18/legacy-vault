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
                        tuple("checkinIntervalDays", "Check-in interval is required"),
                        tuple("gracePeriodDays", "Grace period is required"),
                        tuple("reminderCount", "Reminder count is required"));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
            "checkinIntervalDays, 0, Check-in interval must be at least 1 day",
            "checkinIntervalDays, -1, Check-in interval must be at least 1 day",
            "checkinIntervalDays, 366, Check-in interval must be at most 365 days",
            "gracePeriodDays, 0, Grace period must be at least 1 day",
            "gracePeriodDays, -7, Grace period must be at least 1 day",
            "gracePeriodDays, 91, Grace period must be at most 90 days",
            "reminderCount, 0, Reminder count must be at least 1",
            "reminderCount, -3, Reminder count must be at least 1",
            "reminderCount, 11, Reminder count must be at most 10"
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
