package com.app.backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AppleLoginRequest(
        @NotBlank String idToken,
        // 애플이 발급한 authorizationCode. 탈퇴 시 연동 해제(revoke)용 토큰 확보에 사용. 없으면 생략 가능.
        String appleAuthorizationCode
) {
}
