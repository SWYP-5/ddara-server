package com.app.backend.domain.user.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FcmTokenRequestTest {

    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = factory.getValidator();

    @Test
    void fcmToken이_비어있으면_검증에_실패한다() {
        // given: 빈 문자열
        assertThat(validator.validate(new FcmTokenRequest(""))).isNotEmpty();
        // given: 공백만
        assertThat(validator.validate(new FcmTokenRequest("   "))).isNotEmpty();
        // given: null
        assertThat(validator.validate(new FcmTokenRequest(null))).isNotEmpty();
    }

    @Test
    void fcmToken이_채워지면_검증을_통과한다() {
        // given
        FcmTokenRequest request = new FcmTokenRequest("example-fcm-registration-token");

        // when & then
        assertThat(validator.validate(request)).isEmpty();
    }
}
