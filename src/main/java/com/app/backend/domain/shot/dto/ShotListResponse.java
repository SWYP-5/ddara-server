package com.app.backend.domain.shot.dto;

import com.app.backend.domain.cycle.entity.CycleStatus;

import java.time.OffsetDateTime;
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
            Long starterUserId,
            Long starterShotId,
            String starterNickname,
            String starterImageUrl,
            boolean starterImageUnderReview,
            CycleStatus status,
            OffsetDateTime deadlineAt
    ) {
    }

    public record MemberShot(
            Long userId,
            Long shotId,
            String nickname,
            String profileImageUrl,
            boolean isStarter,
            String status,          // open / empty / locked / reported
            String imageUrl,
            OffsetDateTime uploadedAt,
            boolean hasUnreadComments
    ) {
    }
}