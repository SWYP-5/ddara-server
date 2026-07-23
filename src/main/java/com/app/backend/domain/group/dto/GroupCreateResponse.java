package com.app.backend.domain.group.dto;

import com.app.backend.domain.group.entity.Group;

import com.app.backend.global.util.KstTime;
import java.time.OffsetDateTime;

public record GroupCreateResponse(
        Long groupId,
        String name,
        String description,
        String inviteCode,
        OffsetDateTime createdAt
) {
    public static GroupCreateResponse from(Group group) {
        return new GroupCreateResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getInviteCode(),
                KstTime.toOffset(group.getCreatedAt())
        );
    }
}