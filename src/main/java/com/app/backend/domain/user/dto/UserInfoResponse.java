package com.app.backend.domain.user.dto;

import com.app.backend.domain.user.entity.User;
import com.app.backend.global.util.KstTime;

import java.time.OffsetDateTime;

public record UserInfoResponse(
        Long id,
        String name,
        String profileImageUrl,
        String provider,
        OffsetDateTime createdAt   // KST 오프셋(+09:00) 포함
) {
    public static UserInfoResponse from(User user) {
        return new UserInfoResponse(
                user.getId(),
                user.getName(),
                user.getProfileImageUrl(),
                user.getProvider().name(),
                KstTime.toOffset(user.getCreatedAt())
        );
    }
}
