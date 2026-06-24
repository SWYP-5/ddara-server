package com.app.backend.domain.group.dto;

public record GroupInviteResponse(
        Long groupId,
        String inviteCode,
        String inviteLink
) {
}