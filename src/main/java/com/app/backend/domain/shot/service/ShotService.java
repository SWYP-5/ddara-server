package com.app.backend.domain.shot.service;

import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.entity.CycleStatus;
import com.app.backend.domain.cycle.repository.CycleRepository;
import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.shot.dto.ShotListResponse;
import com.app.backend.domain.shot.dto.ShotResponse;
import com.app.backend.domain.shot.dto.ShotUploadRequest;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShotService {

    private final CycleRepository cycleRepository;
    private final MembershipRepository membershipRepository;
    private final ShotRepository shotRepository;
    private final UserRepository userRepository;

    public ShotService(CycleRepository cycleRepository,
                       MembershipRepository membershipRepository,
                       ShotRepository shotRepository,
                       UserRepository userRepository) {
        this.cycleRepository = cycleRepository;
        this.membershipRepository = membershipRepository;
        this.shotRepository = shotRepository;
        this.userRepository = userRepository;
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

    @Transactional(readOnly = true)
    public ShotListResponse getShots(Long userId, Long cycleId) {
        Cycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new CustomException(ErrorCode.CYCLE_NOT_FOUND));

        if (!membershipRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(cycle.getGroupId(), userId)) {
            throw new CustomException(ErrorCode.NOT_GROUP_MEMBER);
        }

        List<Membership> members = membershipRepository.findByGroupIdAndLeftAtIsNull(cycle.getGroupId());
        Map<Long, User> usersById = userRepository.findAllById(
                        members.stream().map(Membership::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Shot> shotsByUser = shotRepository.findByCycleIdAndDeletedAtIsNull(cycleId).stream()
                .collect(Collectors.toMap(Shot::getUserId, Function.identity()));

        boolean viewerUploaded = shotsByUser.containsKey(userId);
        Long starterId = cycle.getStarterUserId();

        List<ShotListResponse.MemberShot> memberShots = members.stream()
                .map(membership -> {
                    Long memberId = membership.getUserId();
                    User user = usersById.get(memberId);
                    boolean isStarter = memberId.equals(starterId);
                    Shot shot = shotsByUser.get(memberId);

                    String status;
                    String imageUrl;
                    LocalDateTime uploadedAt;
                    if (shot == null) {
                        status = "empty";
                        imageUrl = null;
                        uploadedAt = null;
                    } else {
                        boolean canSee = viewerUploaded || isStarter || memberId.equals(userId);
                        status = canSee ? "open" : "locked";
                        imageUrl = canSee ? shot.getImageUrl() : null;
                        uploadedAt = shot.getUploadedAt();
                    }
                    return new ShotListResponse.MemberShot(
                            memberId,
                            user != null ? user.getNickname() : null,
                            user != null ? user.getProfileImageUrl() : null,
                            isStarter,
                            status,
                            imageUrl,
                            uploadedAt);
                })
                .sorted(Comparator.comparing((ShotListResponse.MemberShot ms) -> !ms.isStarter()))
                .toList();

        return new ShotListResponse(viewerUploaded, memberShots);
    }
}
