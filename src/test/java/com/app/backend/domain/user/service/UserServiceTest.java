package com.app.backend.domain.user.service;

import com.app.backend.domain.auth.repository.RefreshTokenRepository;
import com.app.backend.domain.user.dto.NotificationSettingsRequest;
import com.app.backend.domain.user.dto.NotificationSettingsResponse;
import com.app.backend.domain.user.dto.ProfileImageResponse;
import com.app.backend.domain.user.dto.UserInfoResponse;
import com.app.backend.domain.user.entity.AuthProvider;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileImageStorage profileImageStorage;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository, profileImageStorage, objectMapper, refreshTokenRepository);
    }

    private User createUser() {
        return User.builder()
                .provider(AuthProvider.KAKAO)
                .providerId("kakao-123")
                .email("minju@kakao.com")
                .name("민주")
                .profileImageUrl(null)
                .build();
    }

    @Test
    void 내_정보를_조회하면_본인_프로필_정보를_반환한다() {
        // given: id 1번 유저가 DB에 있다고 가정
        User user = createUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        UserInfoResponse response = userService.getMyInfo(1L);

        // then
        assertThat(response.name()).isEqualTo("민주");
        assertThat(response.email()).isEqualTo("minju@kakao.com");
        assertThat(response.provider()).isEqualTo("KAKAO");
        assertThat(response.profileImageUrl()).isNull();
    }

    @Test
    void 유저가_없으면_USER_NOT_FOUND_예외를_던진다() {
        // given: DB에 해당 유저가 없음
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getMyInfo(999L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 프로필_이미지를_변경하면_저장하고_URL을_반환한다() {
        // given: 유효한 jpeg 이미지를 올리면, 저장소가 URL을 돌려준다
        User user = createUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        MultipartFile image = new MockMultipartFile(
                "image", "me.jpg", "image/jpeg", new byte[]{1, 2, 3});
        given(profileImageStorage.store(image))
                .willReturn("http://localhost:8080/images/profiles/abc.jpg");

        // when
        ProfileImageResponse response = userService.updateProfileImage(1L, image);

        // then: 응답·엔티티 모두 새 URL로 갱신
        assertThat(response.profileImageUrl())
                .isEqualTo("http://localhost:8080/images/profiles/abc.jpg");
        assertThat(user.getProfileImageUrl())
                .isEqualTo("http://localhost:8080/images/profiles/abc.jpg");
    }

    @Test
    void 이미지_없이_요청하면_프로필_이미지를_초기화한다() {
        // given: 이미 프로필 이미지가 있는 유저
        User user = createUser();
        user.updateProfileImage("http://localhost:8080/images/profiles/old.jpg");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when: image 없이(null) 요청
        ProfileImageResponse response = userService.updateProfileImage(1L, null);

        // then: 디폴트로 초기화(null), 저장소는 호출되지 않음
        assertThat(response.profileImageUrl()).isNull();
        assertThat(user.getProfileImageUrl()).isNull();
        verify(profileImageStorage, never()).store(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 지원하지_않는_형식이면_INVALID_IMAGE_FILE_예외를_던진다() {
        // given: jpg/png가 아닌 파일
        User user = createUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        MultipartFile notImage = new MockMultipartFile(
                "image", "bad.txt", "text/plain", new byte[]{1, 2, 3});

        // when & then
        assertThatThrownBy(() -> userService.updateProfileImage(1L, notImage))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_IMAGE_FILE);
    }

    @Test
    void 프로필_이미지_변경시_유저가_없으면_USER_NOT_FOUND_예외를_던진다() {
        // given
        given(userRepository.findById(999L)).willReturn(Optional.empty());
        MultipartFile image = new MockMultipartFile(
                "image", "me.jpg", "image/jpeg", new byte[]{1, 2, 3});

        // when & then
        assertThatThrownBy(() -> userService.updateProfileImage(999L, image))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 알림설정이_미설정이면_전부_true를_반환한다() {
        // given: notification_prefs가 null인 유저
        User user = createUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        NotificationSettingsResponse response = userService.getNotificationSettings(1L);

        // then
        assertThat(response.allowAll()).isTrue();
        assertThat(response.activity().followShot()).isTrue();
        assertThat(response.activity().deadlineVote()).isTrue();
        assertThat(response.etc().memberJoin()).isTrue();
    }

    @Test
    void 알림설정_조회시_저장된_값을_그대로_반환한다() {
        // given: 일부 토글이 꺼진 설정이 저장돼 있음
        User user = createUser();
        user.updateNotificationPrefs(
                "{\"allowAll\":true,\"activity\":{\"followShot\":false,\"deadlineVote\":true},\"etc\":{\"memberJoin\":false}}");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        NotificationSettingsResponse response = userService.getNotificationSettings(1L);

        // then
        assertThat(response.allowAll()).isTrue();
        assertThat(response.activity().followShot()).isFalse();
        assertThat(response.activity().deadlineVote()).isTrue();
        assertThat(response.etc().memberJoin()).isFalse();
    }

    @Test
    void 알림설정_조회시_유저가_없으면_USER_NOT_FOUND_예외를_던진다() {
        // given
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getNotificationSettings(999L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 알림설정을_변경하면_전체교체로_저장하고_반환한다() {
        // given
        User user = createUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        NotificationSettingsRequest request = new NotificationSettingsRequest(
                true,
                new NotificationSettingsRequest.Activity(false, true),
                new NotificationSettingsRequest.Etc(true));

        // when
        NotificationSettingsResponse response = userService.updateNotificationSettings(1L, request);

        // then: 응답이 변경값을 반영
        assertThat(response.allowAll()).isTrue();
        assertThat(response.activity().followShot()).isFalse();
        assertThat(response.activity().deadlineVote()).isTrue();
        assertThat(response.etc().memberJoin()).isTrue();
        // then: 엔티티에 JSON으로 저장됨
        assertThat(user.getNotificationPrefs()).contains("\"followShot\":false");
    }

    @Test
    void 알림설정_변경시_유저가_없으면_USER_NOT_FOUND_예외를_던진다() {
        // given
        given(userRepository.findById(999L)).willReturn(Optional.empty());
        NotificationSettingsRequest request = new NotificationSettingsRequest(
                true,
                new NotificationSettingsRequest.Activity(true, true),
                new NotificationSettingsRequest.Etc(true));

        // when & then
        assertThatThrownBy(() -> userService.updateNotificationSettings(999L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 회원탈퇴하면_익명화하고_탈퇴시각을_기록하고_토큰을_폐기한다() {
        // given: 프로필이 채워진 유저
        User user = createUser();
        user.updateProfileImage("http://localhost:8080/images/profiles/me.jpg");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userService.withdraw(1L);

        // then: soft delete + 익명화
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getName()).isEqualTo("탈퇴한사용자");
        assertThat(user.getEmail()).isNull();
        assertThat(user.getProfileImageUrl()).isNull();
        // then: refresh token 폐기
        verify(refreshTokenRepository).deleteByUserId(1L);
    }

    @Test
    void 탈퇴시_유저가_없으면_USER_NOT_FOUND_예외를_던진다() {
        // given
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.withdraw(999L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(refreshTokenRepository, never()).deleteByUserId(999L);
    }
}
