package com.app.backend.domain.cycle.dto;

import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.entity.CycleStatus;
import com.app.backend.domain.shot.entity.Shot;
import com.app.backend.domain.shot.entity.ShotType;

import com.app.backend.global.util.KstTime;
import java.time.OffsetDateTime;

public record CycleCreateResponse(
        Long cycleId,
        Long groupId,
        int cycleNumber,
        String topic,
        Long starterUserId,
        CycleStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime deadlineAt,
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
                KstTime.toOffset(cycle.getStartedAt()),
                KstTime.toOffset(cycle.getDeadlineAt()),
                new StarterShot(shot.getId(), shot.getImageUrl(), shot.getType())
        );
    }

    public record StarterShot(Long shotId, String imageUrl, ShotType type) {
    }
}
