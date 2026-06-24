package com.app.backend.domain.group.dto;

import com.app.backend.domain.group.entity.Group;

public record GroupJoinResponse(
        Long groupId,
        String name
) {
    public static GroupJoinResponse from(Group group) {
        return new GroupJoinResponse(group.getId(), group.getName());
    }
}