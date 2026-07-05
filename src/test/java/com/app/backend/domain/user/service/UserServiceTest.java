package com.app.backend.domain.user.service;

import com.app.backend.domain.auth.repository.RefreshTokenRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.notification.repository.NotificationRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private MembershipRepository membershipRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 우리 S3 버킷의 profiles/ 경로 URL (테스트 값)
    private static final String VALID_S3_URL =
            "https://ddara-images.s3.ap-northeast-2.amazonaws.com/profiles/abc.jpg";

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository, objectMapper, refreshTokenRepository,
                notificationRepository, membershipRepository,
                "ddara-images", "ap-northeast-2");
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

        // then: email은 응답에서 제외됨 (U-01)
        assertThat(response.name()).isEqualTo("민주");
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
    void 프로필_이미지를_변경하면_URL을_저장하고_반환한다() {
        // given: presign으로 우리 S3에 올린 이미지 URL
        User user = createUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        ProfileImageResponse response = userService.updateProfileImage(1L, VALID_S3_URL);

        // then: 응답·엔티티 모두 새 URL로 갱신
        assertThat(response.profileImageUrl()).isEqualTo(VALID_S3_URL);
        assertThat(user.getProfileImageUrl()).isEqualTo(VALID_S3_URL);
    }

    @Test
    void 이미지_없이_요청하면_프로필_이미지를_초기화한다() {
        // given: 이미 프로필 이미지가 있는 유저
        User user = createUser();
        user.updateProfileImage(VALID_S3_URL);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when: imageUrl 없이(null) 요청
        ProfileImageResponse response = userService.updateProfileImage(1L, null);

        // then: 디폴트로 초기화(null)
        assertThat(response.profileImageUrl()).isNull();
        assertThat(user.getProfileImageUrl()).isNull();
    }

    @Test
    void 우리_S3_경로가_아닌_URL이면_INVALID_IMAGE_FILE_예외를_던진다() {
        // given: 외부/임의 URL — 보안상 임의 URL 저장 방지
        User user = createUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when & then: 외부 도메인 거부
        assertThatThrownBy(() ->
                userService.updateProfileImage(1L, "https://evil.com/hack.jpg"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_IMAGE_FILE);

        // when & then: 우리 버킷이라도 profiles/ 아닌 경로(shots/) 거부
        assertThatThrownBy(() -> userService.updateProfileImage(1L,
                "https://ddara-images.s3.ap-northeast-2.amazonaws.com/shots/x.jpg"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_IMAGE_FILE);
    }

    @Test
    void 프로필_이미지_변경시_유저가_없으면_USER_NOT_FOUND_예외를_던진다() {
        // given
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.updateProfileImage(999L, VALID_S3_URL))
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

        // then: soft delete + 익명화 (데이터는 5일 보존 — 행 자체는 남는다)
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getName()).isEqualTo("탈퇴한사용자");
        assertThat(user.getEmail()).isNull();
        assertThat(user.getProfileImageUrl()).isNull();
        // then: 같은 소셜 계정으로 신규가입할 수 있도록 provider_id의 UNIQUE 자리를 비운다
        assertThat(user.getProviderId()).isNotEqualTo("kakao-123");
        // then: refresh token 폐기(세션 즉시 종료)
        verify(refreshTokenRepository).deleteByUserId(1L);
        // then: 알림·멤버십은 아직 지우지 않는다(5일 보존 후 일괄 삭제)
        verify(notificationRepository, never()).deleteByUserId(1L);
    }

    @Test
    void 탈퇴한_사용자의_정보를_조회하면_USER_NOT_FOUND_예외를_던진다() {
        // given: soft delete된 유저 (탈퇴 직후 남은 access token으로 접근하는 상황)
        User user = createUser();
        user.withdraw(LocalDateTime.now());
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> userService.getMyInfo(1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 탈퇴_보존기간이_지난_사용자는_관련데이터와_함께_완전_삭제한다() {
        // given: 보존기간(5일)이 지나 완전 삭제 대상인 탈퇴 유저 (id=10)
        User expired = createUser();
        expired.withdraw(LocalDateTime.now().minusDays(6));
        ReflectionTestUtils.setField(expired, "id", 10L);
        given(userRepository.findByDeletedAtBefore(any())).willReturn(List.of(expired));

        // when
        userService.purgeWithdrawnUsers();

        // then: 알림·멤버십·사용자 행까지 물리 삭제
        verify(notificationRepository).deleteByUserId(10L);
        verify(membershipRepository).deleteByUserId(10L);
        verify(userRepository).delete(expired);
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

    @Test
    void FCM_토큰을_등록하면_저장한다() {
        // given
        User user = createUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userService.registerFcmToken(1L, "fcm-token-1");

        // then
        assertThat(user.getFcmToken()).isEqualTo("fcm-token-1");
    }

    @Test
    void FCM_토큰_재등록시_기존_값을_덮어쓴다() {
        // given: 이미 토큰이 등록된 유저
        User user = createUser();
        user.updateFcmToken("old-token");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userService.registerFcmToken(1L, "new-token");

        // then
        assertThat(user.getFcmToken()).isEqualTo("new-token");
    }

    @Test
    void FCM_토큰_등록시_유저가_없으면_USER_NOT_FOUND_예외를_던진다() {
        // given
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.registerFcmToken(999L, "fcm-token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void FCM_토큰을_해제하면_null로_만든다() {
        // given: 토큰이 등록된 유저 (로그아웃 상황)
        User user = createUser();
        user.updateFcmToken("some-token");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userService.clearFcmToken(1L);

        // then
        assertThat(user.getFcmToken()).isNull();
    }
}
