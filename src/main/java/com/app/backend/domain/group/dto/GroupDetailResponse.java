package com.app.backend.domain.group.dto;

import com.app.backend.domain.group.entity.Group;

import java.time.LocalDateTime;
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
        boolean canStartCycle,
        int myCycleCount,
        int totalCycleCount,
        LocalDateTime createdAt
) {
    public static GroupDetailResponse of(Group group, List<MemberResponse> members,
                                         CurrentCycleDetailResponse currentCycle, boolean canStartCycle,
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
                canStartCycle,
                myCycleCount,
                totalCycleCount,
                group.getCreatedAt()
        );
    }
}