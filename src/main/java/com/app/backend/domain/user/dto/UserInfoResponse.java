package com.app.backend.domain.user.dto;

import com.app.backend.domain.user.entity.User;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record UserInfoResponse(
        Long id,
        String name,
        String profileImageUrl,
        String provider,
        OffsetDateTime createdAt   // KST 오프셋(+09:00) 포함
) {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static UserInfoResponse from(User user) {
        return new UserInfoResponse(
                user.getId(),
                user.getName(),
                user.getProfileImageUrl(),
                user.getProvider().name(),
                user.getCreatedAt() == null ? null
                        : user.getCreatedAt().atZone(SEOUL).toOffsetDateTime()
        );
    }
}
