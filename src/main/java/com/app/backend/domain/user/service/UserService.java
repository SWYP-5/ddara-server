package com.app.backend.domain.user.service;

import com.app.backend.domain.auth.repository.RefreshTokenRepository;
import com.app.backend.domain.user.dto.NotificationSettingsRequest;
import com.app.backend.domain.user.dto.NotificationSettingsResponse;
import com.app.backend.domain.user.dto.ProfileImageResponse;
import com.app.backend.domain.user.dto.UserInfoResponse;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final RefreshTokenRepository refreshTokenRepository;
    // 프로필 이미지로 허용할 S3 URL 접두사 (우리 버킷의 profiles/ 경로만)
    private final String profileImageUrlPrefix;

    public UserService(UserRepository userRepository,
                       ObjectMapper objectMapper,
                       RefreshTokenRepository refreshTokenRepository,
                       @Value("${aws.s3.bucket}") String bucket,
                       @Value("${aws.s3.region}") String region) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.refreshTokenRepository = refreshTokenRepository;
        this.profileImageUrlPrefix =
                "https://" + bucket + ".s3." + region + ".amazonaws.com/profiles/";
    }

    @Transactional(readOnly = true)
    public UserInfoResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return UserInfoResponse.from(user);
    }

    @Transactional
    public ProfileImageResponse updateProfileImage(Long userId, String imageUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // imageUrl 없음 → 디폴트 아바타로 초기화
        if (imageUrl == null || imageUrl.isBlank()) {
            user.updateProfileImage(null);
            return new ProfileImageResponse(null);
        }

        // 보안: 우리 S3 버킷의 profiles/ 경로 URL만 허용 (임의 URL 저장 방지)
        if (!imageUrl.startsWith(profileImageUrlPrefix)) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_FILE);
        }

        user.updateProfileImage(imageUrl);
        return new ProfileImageResponse(imageUrl);
    }

    @Transactional(readOnly = true)
    public NotificationSettingsResponse getNotificationSettings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String prefs = user.getNotificationPrefs();
        if (prefs == null || prefs.isBlank()) {
            return NotificationSettingsResponse.allOn();   // 미설정 = 전체 on
        }
        try {
            return objectMapper.readValue(prefs, NotificationSettingsResponse.class);
        } catch (JsonProcessingException e) {
            return NotificationSettingsResponse.allOn();   // 깨진 값이면 기본값
        }
    }

    @Transactional
    public NotificationSettingsResponse updateNotificationSettings(
            Long userId, NotificationSettingsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        try {
            // 전체 교체 — 요청 묶음을 그대로 JSON으로 저장
            user.updateNotificationPrefs(objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        return new NotificationSettingsResponse(
                request.allowAll(),
                new NotificationSettingsResponse.Activity(
                        request.activity().followShot(), request.activity().deadlineVote()),
                new NotificationSettingsResponse.Etc(request.etc().memberJoin()));
    }

    /** FCM 토큰 등록(U-06). 유저당 1개 — 재등록 시 덮어쓴다. */
    @Transactional
    public void registerFcmToken(Long userId, String fcmToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.updateFcmToken(fcmToken);
    }

    /** FCM 토큰 제거(U-06). 로그아웃 시 auth 도메인(오지원)에서 호출하는 연동 지점. */
    @Transactional
    public void clearFcmToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.clearFcmToken();
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        user.withdraw(LocalDateTime.now());        // soft delete + 익명화
        refreshTokenRepository.deleteByUserId(userId);   // refresh token 폐기
    }
}