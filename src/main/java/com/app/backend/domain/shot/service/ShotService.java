package com.app.backend.domain.shot.service;

import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.entity.CycleStatus;
import com.app.backend.domain.cycle.repository.CycleRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.shot.dto.ShotResponse;
import com.app.backend.domain.shot.dto.ShotUploadRequest;
import com.app.backend.domain.shot.entity.Shot;
import com.app.backend.domain.shot.entity.ShotType;
import com.app.backend.domain.shot.repository.ShotRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShotService {

    private final CycleRepository cycleRepository;
    private final MembershipRepository membershipRepository;
    private final ShotRepository shotRepository;

    public ShotService(CycleRepository cycleRepository,
                       MembershipRepository membershipRepository,
                       ShotRepository shotRepository) {
        this.cycleRepository = cycleRepository;
        this.membershipRepository = membershipRepository;
        this.shotRepository = shotRepository;
    }

    @Transactional
    public ShotResponse uploadShot(Long userId, Long cycleId, ShotUploadRequest request) {
        Cycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new CustomException(ErrorCode.CYCLE_NOT_FOUND));

        if (cycle.getStatus() != CycleStatus.IN_PROGRESS) {
            throw new CustomException(ErrorCode.CYCLE_CLOSED);
        }
        if (!membershipRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(cycle.getGroupId(), userId)) {
            throw new CustomException(ErrorCode.NOT_GROUP_MEMBER);
        }
        if (cycle.getStarterUserId().equals(userId)) {
            throw new CustomException(ErrorCode.STARTER_CANNOT_UPLOAD);
        }

        // 사진이 있으면 교체, 없으면 생성
        Shot shot = shotRepository.findByCycleIdAndUserId(cycleId, userId)
                .map(existing -> {
                    existing.changeImageUrl(request.imageUrl());
                    return existing;
                })
                .orElseGet(() -> shotRepository.save(Shot.builder()
                        .cycleId(cycleId)
                        .userId(userId)
                        .type(ShotType.MEMBER)
                        .imageUrl(request.imageUrl())
                        .build()));

        // 전원 업로드 시 자동 마감
        long activeMembers = membershipRepository.countByGroupIdAndLeftAtIsNull(cycle.getGroupId());
        long shotCount = shotRepository.countByCycleIdAndDeletedAtIsNull(cycleId);
        if (shotCount >= activeMembers) {
            cycle.complete();
        }

        return ShotResponse.from(shot);
    }
}
