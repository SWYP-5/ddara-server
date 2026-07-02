package com.app.backend.domain.shot.dto;

import com.app.backend.domain.cycle.entity.CycleStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ShotListResponse(
        Long groupId,
        String groupName,
        CycleBanner cycle,
        boolean viewerUploaded,
        List<MemberShot> members
) {
    public record CycleBanner(
            Long cycleId,
            Integer cycleNumber,
            String topic,
            String starterNickname,
            String starterImageUrl,
            CycleStatus status,
            LocalDateTime deadlineAt
    ) {
    }

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