package com.app.backend.domain.cycle.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PastCyclesResponse(
        Stats stats,
        List<PastCycle> cycles
) {
    public record Stats(
            long myCount,
            long totalCount
    ) {
    }

    public record PastCycle(
            Long cycleId,
            String topic,
            String thumbnailUrl,
            boolean thumbnailUnderReview,
            Long starterUserId,
            long participantCount,
            List<Participant> participants,
            OffsetDateTime date
    ) {
    }

    public record Participant(
            Long userId,
            String profileImageUrl
    ) {
    }
}