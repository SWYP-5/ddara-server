package com.app.backend.domain.shot.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ShotListResponse(
        boolean viewerUploaded,
        List<MemberShot> members
) {
    public record MemberShot(
            Long userId,
            String nickname,
            String profileImageUrl,
            boolean isStarter,
            String status,          // open / empty / locked
            String imageUrl,
            LocalDateTime uploadedAt
    ) {
    }
}