package com.app.backend.domain.auth.service;

import com.app.backend.domain.auth.dto.SignupRequest;
import com.app.backend.domain.auth.entity.RefreshToken;
import com.app.backend.domain.auth.jwt.JwtProvider;
import com.app.backend.domain.auth.oauth.OAuthClient;
import com.app.backend.domain.auth.oauth.OAuthClientResolver;
import com.app.backend.domain.auth.oauth.OAuthUserInfo;
import com.app.backend.domain.auth.repository.RefreshTokenRepository;
import com.app.backend.domain.user.entity.AuthProvider;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private OAuthClientResolver oAuthClientResolver;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                oAuthClientResolver, userRepository, jwtProvider,
                refreshTokenRepository, 2_592_000_000L);
    }

    @Test
    void 로그아웃하면_FCM토큰을_비우고_리프레시토큰을_삭제한다() {
        // given: 1번 유저의 refresh token + 등록된 FCM 토큰
        User user = User.builder()
                .provider(AuthProvider.KAKAO)
                .providerId("p")
                .name("멤버")
                .build();
        user.updateFcmToken("device-token");
        RefreshToken refreshToken = RefreshToken.builder()
                .token("rt-abc")
                .userId(1L)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        given(refreshTokenRepository.findByToken("rt-abc")).willReturn(Optional.of(refreshToken));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        authService.logout("rt-abc");

        // then: 로그아웃 기기로 푸시가 가지 않도록 FCM 토큰 제거 + 세션 종료
        assertThat(user.getFcmToken()).isNull();
        verify(refreshTokenRepository).deleteByToken("rt-abc");
    }

    @Test
    void 없는_토큰으로_로그아웃해도_예외없이_넘어간다() {
        // given
        given(refreshTokenRepository.findByToken("unknown")).willReturn(Optional.empty());

        // when & then: 멱등 — 이미 로그아웃된 상태와 동일하게 조용히 통과
        assertThatCode(() -> authService.logout("unknown")).doesNotThrowAnyException();
    }

    @Test
    void 탈퇴한_소셜계정으로_재가입하면_재활성화가_아니라_새_계정을_만든다() {
        // given: 탈퇴 시 provider_id를 비워(mangle) 원래 소셜번호로는 조회되지 않는 상태 (A정책)
        OAuthClient kakaoClient = mock(OAuthClient.class);
        given(oAuthClientResolver.resolve(AuthProvider.KAKAO)).willReturn(kakaoClient);
        given(kakaoClient.getUserInfo("access-token"))
                .willReturn(new OAuthUserInfo("kakao-123", "민주"));
        given(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "kakao-123"))
                .willReturn(Optional.empty());   // 탈퇴 계정은 자리가 비워져 조회되지 않음
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        authService.signup(new SignupRequest(AuthProvider.KAKAO, "access-token", true));

        // then: 기존(탈퇴) 계정 재활성화가 아니라 완전 새 계정 생성
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getProviderId()).isEqualTo("kakao-123");
        assertThat(saved.getValue().getName()).isEqualTo("민주");
        assertThat(saved.getValue().isWithdrawn()).isFalse();
    }
}
