package com.app.backend.domain.auth.dto;

import com.app.backend.domain.user.entity.AuthProvider;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SignupRequest(
        @NotNull AuthProvider provider,
        @NotBlank String accessToken,
        // 애플 가입 시에만 사용. 연동 해제(revoke)용 refresh_token 확보에 쓰인다.
        String appleAuthorizationCode,
        @AssertTrue(message = "약관에 동의해야 합니다.") boolean termsAgreed
) {
}
