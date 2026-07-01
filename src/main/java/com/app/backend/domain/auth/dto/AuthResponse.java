package com.app.backend.domain.auth.dto;

import com.app.backend.domain.user.entity.User;

public record AuthResponse(
        boolean isNewUser,
        String accessToken,
        String refreshToken,
        UserInfo user
) {

    public record UserInfo(
            Long id,
            String name,
            String profileImageUrl
    ) {
        public static UserInfo from(User user) {
            return new UserInfo(user.getId(), user.getName(), user.getProfileImageUrl());
        }
    }

    public static AuthResponse login(String accessToken, String refreshToken, User user) {
        return new AuthResponse(false, accessToken, refreshToken, UserInfo.from(user));
    }

    public static AuthResponse signupRequired() {
        return new AuthResponse(true, null, null, null);
    }

    public static AuthResponse signup(String accessToken, String refreshToken, User user) {
        return new AuthResponse(true, accessToken, refreshToken, UserInfo.from(user));
    }
}
