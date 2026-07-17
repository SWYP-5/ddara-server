package com.app.backend.domain.group.dto;

import com.app.backend.domain.group.entity.Group;

import java.time.LocalDateTime;

public record GroupListItem(
        Long groupId,
        String name,
        String ownerNickname,
        long memberCount,
        String thumbnailUrl,
        boolean thumbnailUnderReview,
        Long thumbnailUserId,
        CurrentCycleResponse currentCycle,
        LocalDateTime createdAt
) {
    public static GroupListItem of(Group group, String ownerNickname, long memberCount,
                                   String thumbnailUrl, boolean thumbnailUnderReview, Long thumbnailUserId,
                                   CurrentCycleResponse currentCycle) {
        return new GroupListItem(
                group.getId(),
                group.getName(),
                ownerNickname,
                memberCount,
                thumbnailUrl,
                thumbnailUnderReview,
                thumbnailUserId,
                currentCycle,
                group.getCreatedAt()
        );
    }
}