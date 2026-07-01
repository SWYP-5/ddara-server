package com.app.backend.domain.group.dto;

import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.entity.CycleStatus;

import java.time.LocalDateTime;

public record CurrentCycleDetailResponse(
        Long cycleId,
        Integer cycleNumber,
        String topic,
        Long starterUserId,
        String starterImageUrl,
        CycleStatus status,
        LocalDateTime startedAt,
        LocalDateTime deadlineAt
) {
    public static CurrentCycleDetailResponse from(Cycle cycle, String starterImageUrl) {
        return new CurrentCycleDetailResponse(
                cycle.getId(),
                cycle.getCycleNumber(),
                cycle.getTopic(),
                cycle.getStarterUserId(),
                starterImageUrl,
                cycle.getStatus(),
                cycle.getStartedAt(),
                cycle.getDeadlineAt()
        );
    }
}
