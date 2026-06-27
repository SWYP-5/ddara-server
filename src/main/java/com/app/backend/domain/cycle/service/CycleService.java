package com.app.backend.domain.cycle.service;

import com.app.backend.domain.cycle.dto.CreateCycleRequest;
import com.app.backend.domain.cycle.dto.CurrentCycleResponse;
import com.app.backend.domain.cycle.dto.CycleCreateResponse;
import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.entity.CycleStatus;
import com.app.backend.domain.cycle.repository.CycleRepository;
import com.app.backend.domain.group.repository.GroupRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.shot.entity.Shot;
import com.app.backend.domain.shot.entity.ShotType;
import com.app.backend.domain.shot.repository.ShotRepository;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CycleService {

    private static final int MIN_MEMBERS_TO_START = 3;
    private static final int CYCLE_DURATION_HOURS = 24;

    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final CycleRepository cycleRepository;
    private final ShotRepository shotRepository;
    private final UserRepository userRepository;

    public CycleService(GroupRepository groupRepository,
                        MembershipRepository membershipRepository,
                        CycleRepository cycleRepository,
                        ShotRepository shotRepository,
                        UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.cycleRepository = cycleRepository;
        this.shotRepository = shotRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CycleCreateResponse createCycle(Long userId, Long groupId, CreateCycleRequest request) {
        if (!groupRepository.existsById(groupId)) {
            throw new CustomException(ErrorCode.GROUP_NOT_FOUND);
        }
        if (!membershipRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId)) {
            throw new CustomException(ErrorCode.NOT_GROUP_MEMBER);
        }
        if (membershipRepository.countByGroupIdAndLeftAtIsNull(groupId) < MIN_MEMBERS_TO_START) {
            throw new CustomException(ErrorCode.NOT_ENOUGH_MEMBERS);
        }
        if (cycleRepository.existsByGroupIdAndStatus(groupId, CycleStatus.IN_PROGRESS)) {
            throw new CustomException(ErrorCode.CYCLE_ALREADY_IN_PROGRESS);
        }

        LocalDateTime now = LocalDateTime.now();
        int cycleNumber = (int) cycleRepository.countByGroupId(groupId) + 1;

        Cycle cycle = cycleRepository.save(Cycle.builder()
                .groupId(groupId)
                .cycleNumber(cycleNumber)
                .topic(request.topic())
                .starterUserId(userId)
                .startedAt(now)
                .deadlineAt(now.plusHours(CYCLE_DURATION_HOURS))
                .build());

        // 스타터 원본 사진을 Shot(type=starter)으로 함께 저장 (따라찍기 가이드)
        Shot starterShot = shotRepository.save(Shot.builder()
                .cycleId(cycle.getId())
                .userId(userId)
                .type(ShotType.STARTER)
                .imageUrl(request.imageUrl())
                .build());

        return CycleCreateResponse.of(cycle, starterShot);
    }

    @Transactional(readOnly = true)
    public CurrentCycleResponse getCurrentCycle(Long userId, Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new CustomException(ErrorCode.GROUP_NOT_FOUND);
        }
        if (!membershipRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId)) {
            throw new CustomException(ErrorCode.NOT_GROUP_MEMBER);
        }

        return cycleRepository.findByGroupIdAndStatus(groupId, CycleStatus.IN_PROGRESS)
                .map(cycle -> {
                    String starterNickname = userRepository.findById(cycle.getStarterUserId())
                            .map(User::getNickname)
                            .orElse(null);
                    String starterImageUrl = shotRepository
                            .findByCycleIdAndType(cycle.getId(), ShotType.STARTER)
                            .map(Shot::getImageUrl)
                            .orElse(null);
                    return CurrentCycleResponse.of(cycle, starterNickname, starterImageUrl);
                })
                .orElseGet(CurrentCycleResponse::empty);
    }

    // 시작 후 24h(deadline) 지난 진행 중 회차를 일괄 마감 (스케줄러용)
    @Transactional
    public int closeOverdueCycles() {
        List<Cycle> overdue = cycleRepository
                .findByStatusAndDeadlineAtBefore(CycleStatus.IN_PROGRESS, LocalDateTime.now());
        overdue.forEach(Cycle::complete);
        return overdue.size();
    }
}
