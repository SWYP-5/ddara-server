package com.app.backend.domain.auth.apple;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 애플 /auth/token 응답 중 필요한 필드만 매핑. */
public record AppleTokenResponse(
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("access_token") String accessToken
) {
}
