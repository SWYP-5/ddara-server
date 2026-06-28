package com.app.backend.domain.user.controller;

import com.app.backend.domain.user.dto.UserInfoResponse;
import com.app.backend.domain.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // 내 정보 조회 (USER-01)
    @GetMapping("/me")
    public UserInfoResponse getMyInfo(@AuthenticationPrincipal Long userId) {
        return userService.getMyInfo(userId);
    }
}