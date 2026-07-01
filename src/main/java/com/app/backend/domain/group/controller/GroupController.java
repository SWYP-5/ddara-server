package com.app.backend.domain.group.controller;

import com.app.backend.domain.group.dto.CreateGroupRequest;
import com.app.backend.domain.group.dto.GroupCreateResponse;
import com.app.backend.domain.group.dto.GroupDetailResponse;
import com.app.backend.domain.group.dto.GroupJoinResponse;
import com.app.backend.domain.group.dto.GroupNicknameResponse;
import com.app.backend.domain.group.dto.GroupPreviewResponse;
import com.app.backend.domain.group.dto.JoinGroupRequest;
import com.app.backend.domain.group.dto.MyGroupsResponse;
import com.app.backend.domain.group.dto.UpdateGroupNicknameRequest;
import com.app.backend.domain.group.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupCreateResponse createGroup(@AuthenticationPrincipal Long userId,
                                           @Valid @RequestBody CreateGroupRequest request) {
        return groupService.createGroup(userId, request);
    }

    @GetMapping
    public MyGroupsResponse getMyGroups(@AuthenticationPrincipal Long userId) {
        return groupService.getMyGroups(userId);
    }

    @GetMapping("/preview")
    public GroupPreviewResponse previewGroup(@AuthenticationPrincipal Long userId,
                                             @RequestParam String inviteCode) {
        return groupService.previewGroup(userId, inviteCode);
    }

    @PostMapping("/join")
    public GroupJoinResponse joinGroup(@AuthenticationPrincipal Long userId,
                                       @Valid @RequestBody JoinGroupRequest request) {
        return groupService.joinGroup(userId, request.inviteCode(), request.nickname());
    }

    @GetMapping("/{groupId}")
    public GroupDetailResponse getGroupDetail(@AuthenticationPrincipal Long userId,
                                              @PathVariable Long groupId) {
        return groupService.getGroupDetail(userId, groupId);
    }

    @DeleteMapping("/{groupId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveGroup(@AuthenticationPrincipal Long userId,
                           @PathVariable Long groupId) {
        groupService.leaveGroup(userId, groupId);
    }

    @PatchMapping("/{groupId}/members/me/nickname")
    public GroupNicknameResponse updateMyGroupNickname(@AuthenticationPrincipal Long userId,
                                                       @PathVariable Long groupId,
                                                       @Valid @RequestBody UpdateGroupNicknameRequest request) {
        return groupService.updateMyGroupNickname(userId, groupId, request.nickname());
    }
}