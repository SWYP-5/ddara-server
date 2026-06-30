package com.app.backend.domain.user.controller;

import com.app.backend.domain.user.dto.NotificationSettingsResponse;
import com.app.backend.domain.user.dto.ProfileImageResponse;
import com.app.backend.domain.user.dto.UserInfoResponse;
import com.app.backend.domain.user.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    // 프로필 이미지 변경 (U-02). image 없이 요청하면 디폴트 아바타로 초기화.
    @PatchMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileImageResponse updateProfileImage(
            @AuthenticationPrincipal Long userId,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return userService.updateProfileImage(userId, image);
    }

    // 알림 설정 조회 (U-03)
    @GetMapping("/me/notification-settings")
    public NotificationSettingsResponse getNotificationSettings(@AuthenticationPrincipal Long userId) {
        return userService.getNotificationSettings(userId);
    }
}