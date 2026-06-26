package com.app.backend.domain.group.dto;

import com.app.backend.domain.group.entity.Group;

public record GroupPreviewResponse(
        Long groupId,
        String name,
        String description,
        String ownerNickname,
        long memberCount,
        int capacity,
        boolean isFull,
        String latestShotUrl,
        boolean alreadyJoined
) {
    public static GroupPreviewResponse of(Group group, String ownerNickname, long memberCount,
                                          int capacity, String latestShotUrl, boolean alreadyJoined) {
        return new GroupPreviewResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                ownerNickname,
                memberCount,
                capacity,
                memberCount >= capacity,
                latestShotUrl,
                alreadyJoined
        );
    }
}