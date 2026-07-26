package com.app.backend.domain.group.dto;

import com.app.backend.domain.group.entity.Group;

import com.app.backend.global.util.KstTime;
import java.time.OffsetDateTime;

public record GroupListItem(
        Long groupId,
        String name,
        String ownerNickname,
        long memberCount,
        String thumbnailUrl,
        boolean thumbnailUnderReview,
        Long thumbnailUserId,
        CurrentCycleResponse currentCycle,
        OffsetDateTime createdAt,
        boolean showStarterBorder
) {
    public static GroupListItem of(Group group, String ownerNickname, long memberCount,
                                   String thumbnailUrl, boolean thumbnailUnderReview, Long thumbnailUserId,
                                   CurrentCycleResponse currentCycle, boolean showStarterBorder) {
        return new GroupListItem(
                group.getId(),
                group.getName(),
                ownerNickname,
                memberCount,
                thumbnailUrl,
                thumbnailUnderReview,
                thumbnailUserId,
                currentCycle,
                KstTime.toOffset(group.getCreatedAt()),
                showStarterBorder
        );
    }
}