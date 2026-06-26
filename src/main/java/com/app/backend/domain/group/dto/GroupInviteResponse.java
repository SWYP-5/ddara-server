package com.app.backend.domain.group.dto;

import java.time.LocalDateTime;

public record GroupInviteResponse(
        Long groupId,
        String inviteCode,
        String inviteLink,
        LocalDateTime expiresAt
) {
}