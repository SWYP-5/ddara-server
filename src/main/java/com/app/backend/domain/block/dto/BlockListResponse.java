package com.app.backend.domain.block.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BlockListResponse(
        List<BlockedUser> blocks
) {

    public record BlockedUser(
            Long userId,
            String name,
            OffsetDateTime blockedAt
    ) {
    }
}