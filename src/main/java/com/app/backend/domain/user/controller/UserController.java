package com.app.backend.domain.user.controller;

import com.app.backend.domain.user.dto.FcmTokenRequest;
import com.app.backend.domain.user.dto.NotificationSettingsRequest;
import com.app.backend.domain.user.dto.NotificationSettingsResponse;
import com.app.backend.domain.user.dto.ProfileImageRequest;
import com.app.backend.domain.user.dto.ProfileImageResponse;
import com.app.backend.domain.user.dto.UserInfoResponse;
import com.app.backend.domain.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    // 프로필 이미지 변경 (U-02). presign으로 S3에 올린 imageUrl 등록. imageUrl 없으면 디폴트로 초기화.
    @PatchMapping("/me/profile-image")
    public ProfileImageResponse updateProfileImage(
            @AuthenticationPrincipal Long userId,
            @RequestBody ProfileImageRequest request) {
        return userService.updateProfileImage(userId, request.imageUrl());
    }

    // 알림 설정 조회 (U-03)
    @GetMapping("/me/notification-settings")
    public NotificationSettingsResponse getNotificationSettings(@AuthenticationPrincipal Long userId) {
        return userService.getNotificationSettings(userId);
    }

    // 알림 설정 변경 (U-04) — 전체 교체
    @PatchMapping("/me/notification-settings")
    public NotificationSettingsResponse updateNotificationSettings(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody NotificationSettingsRequest request) {
        return userService.updateNotificationSettings(userId, request);
    }

    // 회원 탈퇴 (U-05) — soft delete + 익명화 + 토큰 폐기
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal Long userId) {
        userService.withdraw(userId);
    }

    // FCM 토큰 등록 (U-06) — 유저당 1개, 덮어쓰기
    @PutMapping("/me/fcm-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerFcmToken(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody FcmTokenRequest request) {
        userService.registerFcmToken(userId, request.fcmToken());
    }
}