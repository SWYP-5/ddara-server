package com.app.backend.domain.user.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSettingsRequestTest {

    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = factory.getValidator();

    @Test
    void 필수_boolean_필드가_누락되면_검증에_실패한다() {
        // given: 모든 필드 null
        NotificationSettingsRequest request =
                new NotificationSettingsRequest(null, null, null);

        // when & then
        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void 중첩_필드가_누락되면_검증에_실패한다() {
        // given: activity.followShot 누락
        NotificationSettingsRequest request = new NotificationSettingsRequest(
                true,
                new NotificationSettingsRequest.Activity(null, true),
                new NotificationSettingsRequest.Etc(true));

        // when & then
        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void 모든_필드가_채워지면_검증을_통과한다() {
        // given
        NotificationSettingsRequest request = new NotificationSettingsRequest(
                true,
                new NotificationSettingsRequest.Activity(true, true),
                new NotificationSettingsRequest.Etc(true));

        // when & then
        assertThat(validator.validate(request)).isEmpty();
    }
}
