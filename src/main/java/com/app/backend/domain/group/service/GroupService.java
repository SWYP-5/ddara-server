package com.app.backend.domain.group.service;

import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.entity.CycleStatus;
import com.app.backend.domain.cycle.repository.CycleRepository;
import com.app.backend.domain.group.dto.CreateGroupRequest;
import com.app.backend.domain.group.dto.CurrentCycleDetailResponse;
import com.app.backend.domain.group.dto.CurrentCycleResponse;
import com.app.backend.domain.group.dto.GroupCreateResponse;
import com.app.backend.domain.group.dto.GroupDetailResponse;
import com.app.backend.domain.group.dto.GroupJoinResponse;
import com.app.backend.domain.group.dto.GroupListItem;
import com.app.backend.domain.group.dto.GroupNicknameResponse;
import com.app.backend.domain.group.dto.GroupPreviewResponse;
import com.app.backend.domain.group.dto.MemberResponse;
import com.app.backend.domain.group.dto.MyGroupsResponse;
import com.app.backend.domain.group.entity.Group;
import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.entity.MembershipRole;
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

import java.text.Collator;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private static final int MAX_INVITE_CODE_ATTEMPTS = 10;
    private static final int MAX_GROUPS_PER_USER = 20;
    private static final int MAX_MEMBERS_PER_GROUP = 8;
    private static final int MIN_MEMBERS_TO_START_CYCLE = 3;
    private static final int GROUP_RETENTION_DAYS = 5;

    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final CycleRepository cycleRepository;
    private final ShotRepository shotRepository;
    private final InviteCodeGenerator inviteCodeGenerator;

    public GroupService(GroupRepository groupRepository,
                        MembershipRepository membershipRepository,
                        UserRepository userRepository,
                        CycleRepository cycleRepository,
                        ShotRepository shotRepository,
                        InviteCodeGenerator inviteCodeGenerator) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.cycleRepository = cycleRepository;
        this.shotRepository = shotRepository;
        this.inviteCodeGenerator = inviteCodeGenerator;
    }

    @Transactional
    public GroupCreateResponse createGroup(Long userId, CreateGroupRequest request) {
        if (membershipRepository.countByUserIdAndLeftAtIsNull(userId) >= MAX_GROUPS_PER_USER) {
            throw new CustomException(ErrorCode.GROUP_LIMIT_EXCEEDED);
        }

        Group group = groupRepository.save(Group.builder()
                .name(request.name())
                .description(request.description())
                .ownerUserId(userId)
                .inviteCode(generateUniqueInviteCode())
                .build());

        membershipRepository.save(Membership.builder()
                .groupId(group.getId())
                .userId(userId)
                .nickname(request.nickname())
                .role(MembershipRole.OWNER)
                .joinedAt(LocalDateTime.now())
                .build());

        return GroupCreateResponse.from(group);
    }

    @Transactional(readOnly = true)
    public MyGroupsResponse getMyGroups(Long userId) {
        List<Membership> memberships = membershipRepository.findByUserIdAndLeftAtIsNull(userId);
        if (memberships.isEmpty()) {
            return new MyGroupsResponse(List.of());
        }

        List<Long> groupIds = memberships.stream().map(Membership::getGroupId).toList();
        Map<Long, Group> groupsById = groupRepository.findAllById(groupIds).stream()
                .collect(Collectors.toMap(Group::getId, Function.identity()));

        List<GroupListItem> items = memberships.stream()
                .map(membership -> groupsById.get(membership.getGroupId()))
                .filter(group -> group != null)
                .map(group -> {
                    String ownerNickname = membershipRepository
                            .findByGroupIdAndUserId(group.getId(), group.getOwnerUserId())
                            .map(Membership::getNickname)
                            .orElse(null);
                    long memberCount = membershipRepository.countByGroupIdAndLeftAtIsNull(group.getId());

                    Optional<Cycle> inProgress =
                            cycleRepository.findByGroupIdAndStatus(group.getId(), CycleStatus.IN_PROGRESS);
                    CurrentCycleResponse currentCycle = inProgress
                            .map(c -> new CurrentCycleResponse(c.getId(), c.getTopic(), c.getDeadlineAt()))
                            .orElse(null);

                    String thumbnailUrl = groupThumbnailUrl(group.getId());

                    return GroupListItem.of(group, ownerNickname, memberCount, thumbnailUrl, currentCycle);
                })
                .sorted(Comparator.comparing(GroupListItem::createdAt))
                .toList();

        return new MyGroupsResponse(items);
    }

    @Transactional(readOnly = true)
    public GroupPreviewResponse previewGroup(Long userId, String inviteCode) {
        Group group = groupRepository.findByInviteCodeAndDeletedAtIsNull(inviteCode)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INVITE_CODE));

        String ownerNickname = membershipRepository
                .findByGroupIdAndUserId(group.getId(), group.getOwnerUserId())
                .map(Membership::getNickname)
                .orElse(null);

        long memberCount = membershipRepository.countByGroupIdAndLeftAtIsNull(group.getId());

        boolean alreadyJoined =
                membershipRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(group.getId(), userId);

        List<String> memberAvatars = membershipRepository
                .findTop2ByGroupIdAndLeftAtIsNullOrderByJoinedAtAsc(group.getId()).stream()
                .map(m -> userRepository.findById(m.getUserId())
                        .map(User::getProfileImageUrl)
                        .orElse(null))
                .toList();

        return GroupPreviewResponse.of(
                group, ownerNickname, memberCount, MAX_MEMBERS_PER_GROUP, memberAvatars, alreadyJoined);
    }

    @Transactional(readOnly = true)
    public GroupDetailResponse getGroupDetail(Long userId, Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.GROUP_NOT_FOUND));

        if (!membershipRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId)) {
            throw new CustomException(ErrorCode.NOT_GROUP_MEMBER);
        }

        List<Membership> members = membershipRepository.findByGroupIdAndLeftAtIsNull(groupId);
        Map<Long, User> usersById = userRepository.findAllById(
                        members.stream().map(Membership::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        
        Collator collator = Collator.getInstance(Locale.KOREAN);
        List<MemberResponse> memberResponses = members.stream()
                .sorted(Comparator
                        .comparing((Membership m) -> !m.getUserId().equals(userId))
                        .thenComparing(Membership::getNickname, collator))
                .map(membership -> MemberResponse.of(membership, usersById.get(membership.getUserId())))
                .toList();

        Optional<Cycle> inProgress =
                cycleRepository.findByGroupIdAndStatus(groupId, CycleStatus.IN_PROGRESS);
        CurrentCycleDetailResponse currentCycle = inProgress
                .map(c -> {
                    String starterNickname = members.stream()
                            .filter(m -> m.getUserId().equals(c.getStarterUserId()))
                            .findFirst()
                            .map(Membership::getNickname)
                            .orElse(null);
                    return CurrentCycleDetailResponse.from(c, starterNickname, starterImageUrl(c));
                })
                .orElse(null);

        boolean canStartCycle = inProgress.isEmpty() && members.size() >= MIN_MEMBERS_TO_START_CYCLE;

        // 통계
        List<Cycle> doneCycles =
                cycleRepository.findByGroupIdAndStatusOrderByCycleNumberDesc(groupId, CycleStatus.DONE);
        int myCycleCount = 0;
        for (Cycle done : doneCycles) {
            if (shotRepository.existsByCycleIdAndUserIdAndDeletedAtIsNull(done.getId(), userId)) {
                myCycleCount++;
            }
        }
        int totalCycleCount = doneCycles.size();

        return GroupDetailResponse.of(group, memberResponses, currentCycle, canStartCycle,
                myCycleCount, totalCycleCount);
    }

    @Transactional
    public void leaveGroup(Long userId, Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new CustomException(ErrorCode.GROUP_NOT_FOUND);
        }

        Membership membership = membershipRepository.findByGroupIdAndUserId(groupId, userId)
                .filter(Membership::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_GROUP_MEMBER));

        membership.leave(LocalDateTime.now());
        
        //모임 자동 삭제
        if (membershipRepository.countByGroupIdAndLeftAtIsNull(groupId) == 0) {
            groupRepository.findById(groupId)
                    .ifPresent(group -> group.softDelete(LocalDateTime.now()));
        }
    }

    @Transactional
    public GroupNicknameResponse updateMyGroupNickname(Long userId, Long groupId, String nickname) {
        if (!groupRepository.existsById(groupId)) {
            throw new CustomException(ErrorCode.GROUP_NOT_FOUND);
        }

        Membership membership = membershipRepository.findByGroupIdAndUserId(groupId, userId)
                .filter(Membership::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_GROUP_MEMBER));
        
        if (!membership.getNickname().equals(nickname)
                && membershipRepository.existsByGroupIdAndNicknameAndLeftAtIsNull(groupId, nickname)) {
            throw new CustomException(ErrorCode.DUPLICATE_GROUP_NICKNAME);
        }

        membership.updateNickname(nickname);
        return new GroupNicknameResponse(groupId, nickname);
    }

    @Transactional
    public GroupJoinResponse joinGroup(Long userId, String inviteCode, String nickname) {
        Group group = groupRepository.findByInviteCodeAndDeletedAtIsNull(inviteCode)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INVITE_CODE));

        Membership existing =
                membershipRepository.findByGroupIdAndUserId(group.getId(), userId).orElse(null);

        if (existing != null && existing.isActive()) {
            throw new CustomException(ErrorCode.ALREADY_JOINED_GROUP);
        }

        if (membershipRepository.countByGroupIdAndLeftAtIsNull(group.getId()) >= MAX_MEMBERS_PER_GROUP) {
            throw new CustomException(ErrorCode.GROUP_FULL);
        }

        if (membershipRepository.countByUserIdAndLeftAtIsNull(userId) >= MAX_GROUPS_PER_USER) {
            throw new CustomException(ErrorCode.GROUP_LIMIT_EXCEEDED);
        }

        if (membershipRepository.existsByGroupIdAndNicknameAndLeftAtIsNull(group.getId(), nickname)) {
            throw new CustomException(ErrorCode.DUPLICATE_GROUP_NICKNAME);
        }

        if (existing != null) {
            existing.rejoin(LocalDateTime.now(), nickname);
        } else {
            membershipRepository.save(Membership.builder()
                    .groupId(group.getId())
                    .userId(userId)
                    .nickname(nickname)
                    .role(MembershipRole.MEMBER)
                    .joinedAt(LocalDateTime.now())
                    .build());
        }

        // TODO(NOTI): 합류 성공 시 기존 멤버 전원에게 member_join 알림 발송 (NOTI 도메인 구현 후 연결)

        return GroupJoinResponse.from(group);
    }

    @Transactional
    public int purgeDeletedGroups() {
        List<Group> expired = groupRepository.findByDeletedAtBefore(
                LocalDateTime.now().minusDays(GROUP_RETENTION_DAYS));
        for (Group group : expired) {
            Long groupId = group.getId();
            List<Long> cycleIds = cycleRepository.findByGroupId(groupId).stream()
                    .map(Cycle::getId)
                    .toList();
            if (!cycleIds.isEmpty()) {
                shotRepository.deleteByCycleIdIn(cycleIds);
            }
            cycleRepository.deleteByGroupId(groupId);
            membershipRepository.deleteByGroupId(groupId);
            groupRepository.delete(group);
        }
        return expired.size();
    }

    private String groupThumbnailUrl(Long groupId) {
        Cycle cycle = cycleRepository.findByGroupIdAndStatus(groupId, CycleStatus.IN_PROGRESS)
                .orElseGet(() -> cycleRepository
                        .findTopByGroupIdAndStatusOrderByCycleNumberDesc(groupId, CycleStatus.DONE)
                        .orElse(null));
        return starterImageUrl(cycle);
    }

    private String starterImageUrl(Cycle cycle) {
        if (cycle == null) {
            return null;
        }
        return shotRepository.findByCycleIdAndType(cycle.getId(), ShotType.STARTER)
                .map(Shot::getImageUrl)
                .orElse(null);
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < MAX_INVITE_CODE_ATTEMPTS; attempt++) {
            String code = inviteCodeGenerator.generate();
            if (!groupRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new CustomException(ErrorCode.INVITE_CODE_GENERATION_FAILED);
    }
}