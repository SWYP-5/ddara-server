package com.app.backend.domain.group.dto;

import com.app.backend.domain.group.entity.Group;

import com.app.backend.global.util.KstTime;
import java.time.OffsetDateTime;
import java.util.List;

public record GroupDetailResponse(
        Long groupId,
        String name,
        String description,
        String inviteCode,
        Long ownerUserId,
        int memberCount,
        List<MemberResponse> members,
        CurrentCycleDetailResponse currentCycle,
        NextStarterResponse nextStarter,
        boolean canStartCycle,
        int myCycleCount,
        int totalCycleCount,
        OffsetDateTime createdAt
) {
    public record NextStarterResponse(Long userId, String nickname, OffsetDateTime assignedAt, boolean seen) {
    }

    public static GroupDetailResponse of(Group group, List<MemberResponse> members,
                                         CurrentCycleDetailResponse currentCycle,
                                         NextStarterResponse nextStarter, boolean canStartCycle,
                                         int myCycleCount, int totalCycleCount) {
        return new GroupDetailResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getInviteCode(),
                group.getOwnerUserId(),
                members.size(),
                members,
                currentCycle,
                nextStarter,
                canStartCycle,
                myCycleCount,
                totalCycleCount,
                KstTime.toOffset(group.getCreatedAt())
        );
    }
}