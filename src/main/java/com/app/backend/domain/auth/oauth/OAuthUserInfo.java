package com.app.backend.domain.auth.oauth;

public record OAuthUserInfo(
        String providerId,
        String name
) {
}
