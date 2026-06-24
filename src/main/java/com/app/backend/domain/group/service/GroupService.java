package com.app.backend.domain.group.service;

import com.app.backend.domain.group.dto.CreateGroupRequest;
import com.app.backend.domain.group.dto.GroupCreateResponse;
import com.app.backend.domain.group.dto.GroupListItem;
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