package com.app.backend.domain.cycle.dto;

import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.entity.CycleStatus;

import java.time.LocalDateTime;

public record CurrentCycleResponse(
        CurrentCycle currentCycle
) {
    public static CurrentCycleResponse empty() {
        return new CurrentCycleResponse(null);
    }

    public static CurrentCycleResponse of(Cycle cycle, String starterNickname, String starterImageUrl) {
        return new CurrentCycleResponse(CurrentCycle.of(cycle, starterNickname, starterImageUrl));
    }

    public record CurrentCycle(
            Long cycleId,
            Long groupId,
            int cycleNumber,
            String topic,
            Long starterUserId,
            String starterNickname,
            String starterImageUrl,
            CycleStatus status,
            LocalDateTime startedAt,
            LocalDateTime deadlineAt
    ) {
        static CurrentCycle of(Cycle cycle, String starterNickname, String starterImageUrl) {
            return new CurrentCycle(
                    cycle.getId(),
                    cycle.getGroupId(),
                    cycle.getCycleNumber(),
                    cycle.getTopic(),
                    cycle.getStarterUserId(),
                    starterNickname,
                    starterImageUrl,
                    cycle.getStatus(),
                    cycle.getStartedAt(),
                    cycle.getDeadlineAt()
            );
        }
    }
}
