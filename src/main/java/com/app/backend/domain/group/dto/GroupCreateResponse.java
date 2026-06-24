package com.app.backend.domain.group.dto;

import com.app.backend.domain.group.entity.Group;

import java.time.LocalDateTime;

public record GroupCreateResponse(
        Long groupId,
        String name,
        String description,
        String inviteCode,
        LocalDateTime createdAt
) {
    public static GroupCreateResponse from(Group group) {
        return new GroupCreateResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getInviteCode(),
                group.getCreatedAt()
        );
    }
}