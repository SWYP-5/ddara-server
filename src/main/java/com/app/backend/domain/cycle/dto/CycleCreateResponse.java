package com.app.backend.domain.cycle.dto;

import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.entity.CycleStatus;
import com.app.backend.domain.shot.entity.Shot;
import com.app.backend.domain.shot.entity.ShotType;

import java.time.LocalDateTime;

public record CycleCreateResponse(
        Long cycleId,
        Long groupId,
        int cycleNumber,
        String topic,
        Long starterUserId,
        CycleStatus status,
        LocalDateTime startedAt,
        LocalDateTime deadlineAt,
        StarterShot starterShot
) {
    public static CycleCreateResponse of(Cycle cycle, Shot shot) {
        return new CycleCreateResponse(
                cycle.getId(),
                cycle.getGroupId(),
                cycle.getCycleNumber(),
                cycle.getTopic(),
                cycle.getStarterUserId(),
                cycle.getStatus(),
                cycle.getStartedAt(),
                cycle.getDeadlineAt(),
                new StarterShot(shot.getId(), shot.getImageUrl(), shot.getType())
        );
    }

    public record StarterShot(Long shotId, String imageUrl, ShotType type) {
    }
}
