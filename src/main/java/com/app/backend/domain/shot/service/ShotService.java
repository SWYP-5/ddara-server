package com.app.backend.domain.shot.service;

import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.entity.CycleStatus;
import com.app.backend.domain.cycle.repository.CycleRepository;
import com.app.backend.domain.group.entity.Group;
import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.repository.GroupRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.group.service.NextStarterAssigner;
import com.app.backend.domain.notification.service.NotificationService;
import com.app.backend.domain.shot.dto.ShotListResponse;
import com.app.backend.domain.shot.dto.ShotResponse;
import com.app.backend.domain.shot.dto.ShotUploadRequest;
import com.app.backend.domain.shot.entity.ReviewStatus;
import com.app.backend.domain.shot.entity.Shot;
import com.app.backend.domain.shot.entity.ShotType;
import com.app.backend.domain.shot.repository.ShotRepository;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import com.app.backend.global.util.KstTime;
import com.app.backend.global.util.NicknameOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShotService {

    private final CycleRepository cycleRepository;
    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final ShotRepository shotRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NextStarterAssigner nextStarterAssigner;

    public ShotService(CycleRepository cycleRepository,
                       GroupRepository groupRepository,
                       MembershipRepository membershipRepository,
                       ShotRepository shotRepository,
                       UserRepository userRepository,
                       NotificationService notificationService,
                       NextStarterAssigner nextStarterAssigner) {
        this.cycleRepository = cycleRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.shotRepository = shotRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.nextStarterAssigner = nextStarterAssigner;
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
        // 스타터 원본이 신고 검토중이면 참여 일시중지
        shotRepository.findByCycleIdAndType(cycleId, ShotType.STARTER)
                .filter(Shot::isUnderReview)
                .ifPresent(s -> {
                    throw new CustomException(ErrorCode.CYCLE_PAUSED);
                });

        // 사진이 있으면 교체, 없으면 생성
        Optional<Shot> existingShot = shotRepository.findByCycleIdAndUserId(cycleId, userId);
        boolean isNewUpload = existingShot.isEmpty();
        Shot shot = existingShot
                .map(existing -> {
                    if (existing.isUnderReview()) {
                        throw new CustomException(ErrorCode.SHOT_UNDER_REVIEW);
                    }
                    existing.changeImageUrl(request.imageUrl());
                    if (existing.isRemoved()) {
                        existing.reactivate();
                    }
                    return existing;
                })
                .orElseGet(() -> shotRepository.save(Shot.builder()
                        .cycleId(cycleId)
                        .userId(userId)
                        .type(ShotType.MEMBER)
                        .imageUrl(request.imageUrl())
                        .build()));

        String groupName = groupRepository.findById(cycle.getGroupId())
                .map(Group::getName).orElse("모임");

        if (isNewUpload) {
            String uploaderNickname = membershipRepository
                    .findByGroupIdAndUserId(cycle.getGroupId(), userId)
                    .map(Membership::getNickname).orElse("친구");
            notificationService.createFriendShot(
                    cycle.getGroupId(), groupName, uploaderNickname, userId, cycleId);
        }

        // 전원 업로드 시 자동 마감 (24h 자동마감과 함께 마감되는 2가지 경우 중 하나)
        // 검토중 사진은 업로드로 포함, 운영 삭제 사진은 제외
        long activeMembers = membershipRepository.countByGroupIdAndLeftAtIsNull(cycle.getGroupId());
        long shotCount = shotRepository
                .countByCycleIdAndDeletedAtIsNullAndReviewStatusNot(cycleId, ReviewStatus.REMOVED);
        if (shotCount >= activeMembers) {
            cycle.complete();
            // 조기 마감도 24h 자동마감과 동일하게 모임 멤버 전원에게 마감 알림 발송 (#85)
            notificationService.createCycleCompleted(cycle.getGroupId(), groupName, cycle.getId());
            nextStarterAssigner.assignAfterCycleClosed(cycle.getGroupId(), cycle.getStarterUserId());
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
                .filter(shot -> !shot.isRemoved())
                .collect(Collectors.toMap(Shot::getUserId, Function.identity()));

        boolean viewerUploaded = shotsByUser.containsKey(userId);
        Long starterId = cycle.getStarterUserId();
        boolean cycleDone = cycle.getStatus() == CycleStatus.DONE;

        List<ShotListResponse.MemberShot> memberShots = members.stream()
                .map(membership -> {
                    Long memberId = membership.getUserId();
                    User user = usersById.get(memberId);
                    boolean isStarter = memberId.equals(starterId);
                    Shot shot = shotsByUser.get(memberId);

                    String status;
                    String imageUrl;
                    OffsetDateTime uploadedAt;
                    if (shot == null) {
                        status = "empty";
                        imageUrl = null;
                        uploadedAt = null;
                    } else if (shot.isUnderReview()) {
                        status = "reported";
                        imageUrl = null;
                        uploadedAt = KstTime.toOffset(shot.getUploadedAt());
                    } else {
                        boolean canSee = cycleDone || viewerUploaded || isStarter || memberId.equals(userId);
                        status = canSee ? "open" : "locked";
                        imageUrl = shot.getImageUrl();
                        uploadedAt = KstTime.toOffset(shot.getUploadedAt());
                    }
                    return new ShotListResponse.MemberShot(
                            memberId,
                            shot != null ? shot.getId() : null,
                            membership.getNickname(),
                            user != null ? user.getProfileImageUrl() : null,
                            isStarter,
                            status,
                            imageUrl,
                            uploadedAt);
                })
                .sorted(Comparator
                        .comparing((ShotListResponse.MemberShot ms) -> !ms.userId().equals(userId))
                        .thenComparing(ShotListResponse.MemberShot::nickname, NicknameOrder.COMPARATOR))
                .toList();

        String groupName = groupRepository.findById(cycle.getGroupId())
                .map(Group::getName)
                .orElse(null);
        String starterNickname = membershipRepository
                .findByGroupIdAndUserId(cycle.getGroupId(), starterId)
                .map(Membership::getNickname)
                .orElse(null);
        Shot starterShot = shotsByUser.get(starterId);
        boolean starterUnderReview = starterShot != null && starterShot.isUnderReview();
        ShotListResponse.CycleBanner cycleBanner = new ShotListResponse.CycleBanner(
                cycle.getId(),
                cycle.getCycleNumber(),
                cycle.getTopic(),
                starterId,
                starterShot != null ? starterShot.getId() : null,
                starterNickname,
                starterUnderReview ? null : (starterShot != null ? starterShot.getImageUrl() : null),
                starterUnderReview,
                cycle.getStatus(),
                KstTime.toOffset(cycle.getDeadlineAt()));

        return new ShotListResponse(cycle.getGroupId(), groupName, cycleBanner, viewerUploaded, memberShots);
    }
}
