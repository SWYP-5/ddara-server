package com.app.backend.domain.group.dto;

import java.time.LocalDateTime;

public record CurrentCycleResponse(
        Long cycleId,
        String topic,
        LocalDateTime deadlineAt
) {
}