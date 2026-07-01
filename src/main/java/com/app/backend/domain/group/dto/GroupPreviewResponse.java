package com.app.backend.domain.group.dto;

import com.app.backend.domain.group.entity.Group;

import java.time.LocalDateTime;
import java.util.List;

public record GroupPreviewResponse(
        Long groupId,
        String name,
        String description,
        String ownerNickname,
        long memberCount,
        int capacity,
        boolean isFull,
        List<String> memberAvatars,
        boolean alreadyJoined,
        LocalDateTime createdAt
) {
    public static GroupPreviewResponse of(Group group, String ownerNickname, long memberCount,
                                          int capacity, List<String> memberAvatars, boolean alreadyJoined) {
        return new GroupPreviewResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                ownerNickname,
                memberCount,
                capacity,
                memberCount >= capacity,
                memberAvatars,
                alreadyJoined,
                group.getCreatedAt()
        );
    }
}