package com.app.backend.domain.group.service;

import com.app.backend.domain.group.dto.CreateGroupRequest;
import com.app.backend.domain.group.dto.GroupCreateResponse;
import com.app.backend.domain.group.dto.GroupDetailResponse;
import com.app.backend.domain.group.dto.GroupJoinResponse;
import com.app.backend.domain.group.dto.GroupListItem;
import com.app.backend.domain.group.dto.GroupPreviewResponse;
import com.app.backend.domain.group.dto.MemberResponse;
import com.app.backend.domain.group.dto.MyGroupsResponse;
import com.app.backend.domain.group.entity.Group;
import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.entity.MembershipRole;
import com.app.backend.domain.group.repository.GroupRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
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
public class GroupService {

    private static final int MAX_INVITE_CODE_ATTEMPTS = 10;
    private static final int MAX_GROUPS_PER_USER = 20;

    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final InviteCodeGenerator inviteCodeGenerator;

    public GroupService(GroupRepository groupRepository,
                        MembershipRepository membershipRepository,
                        UserRepository userRepository,
                        InviteCodeGenerator inviteCodeGenerator) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
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

        List<Long> ownerIds = groupsById.values().stream()
                .map(Group::getOwnerUserId)
                .distinct()
                .toList();
        Map<Long, String> ownerNicknameById = userRepository.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        List<GroupListItem> items = memberships.stream()
                .map(membership -> groupsById.get(membership.getGroupId()))
                .filter(group -> group != null)
                .map(group -> {
                    String ownerNickname = ownerNicknameById.get(group.getOwnerUserId());
                    long memberCount = membershipRepository.countByGroupIdAndLeftAtIsNull(group.getId());
                    // currentCycle: CYCLE 도메인 구현 전까지 항상 null (진행 중 회차 없음)
                    return GroupListItem.of(group, ownerNickname, memberCount, null);
                })
                .sorted(Comparator.comparing(GroupListItem::createdAt).reversed())
                .toList();

        return new MyGroupsResponse(items);
    }

    @Transactional(readOnly = true)
    public GroupPreviewResponse previewGroup(Long userId, String inviteCode) {
        Group group = groupRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INVITE_CODE));

        String ownerNickname = userRepository.findById(group.getOwnerUserId())
                .map(User::getNickname)
                .orElse(null);

        long memberCount = membershipRepository.countByGroupIdAndLeftAtIsNull(group.getId());

        boolean alreadyJoined =
                membershipRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(group.getId(), userId);

        // latestShotUrl: SHOT 도메인 구현 전까지 항상 null
        return GroupPreviewResponse.of(group, ownerNickname, memberCount, null, alreadyJoined);
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

        List<MemberResponse> memberResponses = members.stream()
                .sorted(Comparator.comparing(Membership::getJoinedAt))
                .map(membership -> MemberResponse.of(membership, usersById.get(membership.getUserId())))
                .toList();

        // CYCLE 도메인 구현 전까지 null
        return GroupDetailResponse.of(group, memberResponses, null);
    }

    @Transactional
    public GroupJoinResponse joinGroup(Long userId, String inviteCode) {
        Group group = groupRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INVITE_CODE));

        Membership existing =
                membershipRepository.findByGroupIdAndUserId(group.getId(), userId).orElse(null);

        if (existing != null && existing.isActive()) {
            throw new CustomException(ErrorCode.ALREADY_JOINED_GROUP);
        }

        if (membershipRepository.countByUserIdAndLeftAtIsNull(userId) >= MAX_GROUPS_PER_USER) {
            throw new CustomException(ErrorCode.GROUP_LIMIT_EXCEEDED);
        }

        if (existing != null) {
            existing.rejoin(LocalDateTime.now());
        } else {
            membershipRepository.save(Membership.builder()
                    .groupId(group.getId())
                    .userId(userId)
                    .role(MembershipRole.MEMBER)
                    .joinedAt(LocalDateTime.now())
                    .build());
        }

        // TODO(NOTI): 합류 성공 시 기존 멤버 전원에게 member_join 알림 발송 (NOTI 도메인 구현 후 연결)

        return GroupJoinResponse.from(group);
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