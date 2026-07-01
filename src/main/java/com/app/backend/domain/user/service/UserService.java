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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class UserService {

    // 프로필 이미지 허용 형식 (jpg/png)
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of("image/jpeg", "image/png");

    private final UserRepository userRepository;
    private final ProfileImageStorage profileImageStorage;
    private final ObjectMapper objectMapper;
    private final RefreshTokenRepository refreshTokenRepository;

    public UserService(UserRepository userRepository,
                       ProfileImageStorage profileImageStorage,
                       ObjectMapper objectMapper,
                       RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.profileImageStorage = profileImageStorage;
        this.objectMapper = objectMapper;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(readOnly = true)
    public UserInfoResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return UserInfoResponse.from(user);
    }

    @Transactional
    public ProfileImageResponse updateProfileImage(Long userId, MultipartFile image) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // image 없음 → 디폴트 아바타로 초기화
        if (image == null || image.isEmpty()) {
            user.updateProfileImage(null);
            return new ProfileImageResponse(null);
        }

        // 형식 검증 (jpg/png만 허용)
        if (!SUPPORTED_IMAGE_TYPES.contains(image.getContentType())) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_FILE);
        }

        // EC2 로컬에 저장하고 접근 URL만 DB에 보관
        String imageUrl = profileImageStorage.store(image);
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