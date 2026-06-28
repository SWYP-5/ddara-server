package com.app.backend.domain.group.dto;

import java.time.LocalDateTime;

public record PastCycleResponse(
        Long cycleId,
        String topic,
        String thumbnailUrl,
        long participantCount,
        LocalDateTime date
) {
}