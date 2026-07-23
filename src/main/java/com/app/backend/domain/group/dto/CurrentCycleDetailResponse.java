package com.app.backend.domain.group.dto;

import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.entity.CycleStatus;

import com.app.backend.global.util.KstTime;
import java.time.OffsetDateTime;
import java.util.List;

public record CurrentCycleDetailResponse(
        Long cycleId,
        Integer cycleNumber,
        String topic,
        Long starterUserId,
        String starterNickname,
        String starterImageUrl,
        boolean starterImageUnderReview,
        CycleStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime deadlineAt,
        List<Long> uploadedUserIds
) {
    public static CurrentCycleDetailResponse from(Cycle cycle, String starterNickname,
                                                  String starterImageUrl, boolean starterImageUnderReview,
                                                  List<Long> uploadedUserIds) {
        return new CurrentCycleDetailResponse(
                cycle.getId(),
                cycle.getCycleNumber(),
                cycle.getTopic(),
                cycle.getStarterUserId(),
                starterNickname,
                starterImageUrl,
                starterImageUnderReview,
                cycle.getStatus(),
                KstTime.toOffset(cycle.getStartedAt()),
                KstTime.toOffset(cycle.getDeadlineAt()),
                uploadedUserIds
        );
    }
}
