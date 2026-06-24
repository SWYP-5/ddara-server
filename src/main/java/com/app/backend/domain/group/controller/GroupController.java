package com.app.backend.domain.group.controller;

import com.app.backend.domain.group.dto.CreateGroupRequest;
import com.app.backend.domain.group.dto.GroupCreateResponse;
import com.app.backend.domain.group.dto.MyGroupsResponse;
import com.app.backend.domain.group.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    // GROUP-01 모임 생성
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupCreateResponse createGroup(@AuthenticationPrincipal Long userId,
                                           @Valid @RequestBody CreateGroupRequest request) {
        return groupService.createGroup(userId, request);
    }

    // GROUP-02 내 모임 목록
    @GetMapping
    public MyGroupsResponse getMyGroups(@AuthenticationPrincipal Long userId) {
        return groupService.getMyGroups(userId);
    }
}