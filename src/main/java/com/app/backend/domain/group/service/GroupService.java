package com.app.backend.domain.group.service;

import com.app.backend.domain.group.dto.CreateGroupRequest;
import com.app.backend.domain.group.dto.GroupCreateResponse;
import com.app.backend.domain.group.entity.Group;
import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.entity.MembershipRole;
import com.app.backend.domain.group.repository.GroupRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class GroupService {

    private static final int MAX_INVITE_CODE_ATTEMPTS = 10;

    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final InviteCodeGenerator inviteCodeGenerator;

    public GroupService(GroupRepository groupRepository,
                        MembershipRepository membershipRepository,
                        InviteCodeGenerator inviteCodeGenerator) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
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