package com.app.backend.domain.group.dto;

import java.time.LocalDateTime;

public record CurrentCycleDetailResponse(
        Long cycleId,
        Integer cycleNumber,
        String topic,
        Long starterUserId,
        String status,
        LocalDateTime startedAt,
        LocalDateTime deadlineAt
) {
}