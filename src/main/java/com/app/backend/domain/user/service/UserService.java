package com.app.backend.domain.user.service;

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

import java.util.Set;

@Service
public class UserService {

    // 프로필 이미지 허용 형식 (jpg/png)
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of("image/jpeg", "image/png");

    private final UserRepository userRepository;
    private final ProfileImageStorage profileImageStorage;
    private final ObjectMapper objectMapper;

    public UserService(UserRepository userRepository,
                       ProfileImageStorage profileImageStorage,
                       ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.profileImageStorage = profileImageStorage;
        this.objectMapper = objectMapper;
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
}