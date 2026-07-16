package com.app.backend.domain.cycle.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PastCyclesResponse(
        List<PastCycle> cycles
) {
    public record PastCycle(
            Long cycleId,
            String topic,
            String thumbnailUrl,
            Long starterUserId,
            long participantCount,
            LocalDateTime date
    ) {
    }
}