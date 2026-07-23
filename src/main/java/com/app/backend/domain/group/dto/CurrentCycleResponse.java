package com.app.backend.domain.group.dto;

import java.time.OffsetDateTime;

public record CurrentCycleResponse(
        Long cycleId,
        String topic,
        OffsetDateTime deadlineAt
) {
}