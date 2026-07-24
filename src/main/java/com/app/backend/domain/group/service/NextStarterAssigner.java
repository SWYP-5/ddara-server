package com.app.backend.domain.group.service;

import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.entity.CycleStatus;
import com.app.backend.domain.cycle.repository.CycleRepository;
import com.app.backend.domain.group.entity.Group;
import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.repository.GroupRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.notification.service.NotificationService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class NextStarterAssigner {

    private static final int MIN_MEMBERS_TO_START = 3;

    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final CycleRepository cycleRepository;
    private final NotificationService notificationService;

    public NextStarterAssigner(GroupRepository groupRepository,
                               MembershipRepository membershipRepository,
                               CycleRepository cycleRepository,
                               NotificationService notificationService) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.cycleRepository = cycleRepository;
        this.notificationService = notificationService;
    }

    // 회차 마감 직후 다음 스타터를 랜덤 지정
    public void assignAfterCycleClosed(Long groupId, Long justClosedStarterUserId) {
        Group group = assign(groupId, justClosedStarterUserId);
        if (group != null && group.getNextStarterUserId() != null) {
            notificationService.createStarterAssigned(groupId, group.getName());
        }
    }

    // 지정된 스타터가 모임을 나갔을 때 남은 멤버 중 재지정
    public void reassignIfLeft(Long groupId, Long leftUserId) {
        Group group = groupRepository.findById(groupId).orElse(null);
        if (group == null || !leftUserId.equals(group.getNextStarterUserId())) {
            return;
        }
        assign(groupId, lastStarterUserId(groupId));
    }

    private Group assign(Long groupId, Long excludeUserId) {
        Group group = groupRepository.findById(groupId).orElse(null);
        if (group == null || group.getDeletedAt() != null) {
            return null;
        }

        List<Long> activeMemberIds = membershipRepository.findByGroupIdAndLeftAtIsNull(groupId).stream()
                .map(Membership::getUserId)
                .toList();
        if (activeMemberIds.size() < MIN_MEMBERS_TO_START) {
            group.clearNextStarter();
            return group;
        }

        List<Long> candidates = activeMemberIds.stream()
                .filter(id -> !id.equals(excludeUserId))
                .toList();
        if (candidates.isEmpty()) {
            candidates = activeMemberIds;
        }

        Long picked = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        group.assignNextStarter(picked);
        return group;
    }

    // 가장 최근에 실제로 시작된 회차의 스타터 (재지정 시 직전 스타터 제외용)
    private Long lastStarterUserId(Long groupId) {
        return Optional.ofNullable(cycleRepository
                        .findTopByGroupIdAndStatusOrderByCycleNumberDesc(groupId, CycleStatus.DONE)
                        .orElse(null))
                .map(Cycle::getStarterUserId)
                .orElse(null);
    }
}
