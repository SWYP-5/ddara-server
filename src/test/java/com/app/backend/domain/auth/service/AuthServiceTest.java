package com.app.backend.domain.auth.service;

import com.app.backend.domain.auth.entity.RefreshToken;
import com.app.backend.domain.auth.jwt.JwtProvider;
import com.app.backend.domain.auth.oauth.OAuthClientResolver;
import com.app.backend.domain.auth.repository.RefreshTokenRepository;
import com.app.backend.domain.user.entity.AuthProvider;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
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
}
