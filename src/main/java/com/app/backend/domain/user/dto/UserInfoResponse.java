package com.app.backend.domain.user.dto;

import com.app.backend.domain.user.entity.User;

import java.time.LocalDateTime;

public record UserInfoResponse(
        Long id,
        String name,
        String profileImageUrl,
        String provider,
        String email,
        LocalDateTime createdAt
) {
    public static UserInfoResponse from(User user) {
        return new UserInfoResponse(
                user.getId(),
                user.getName(),
                user.getProfileImageUrl(),
                user.getProvider().name(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}
