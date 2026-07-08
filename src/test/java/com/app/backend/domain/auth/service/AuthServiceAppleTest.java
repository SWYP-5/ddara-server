package com.app.backend.domain.auth.service;

import com.app.backend.domain.auth.apple.AppleAuthClient;
import com.app.backend.domain.auth.dto.AuthResponse;
import com.app.backend.domain.auth.dto.SocialLoginRequest;
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

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceAppleTest {

    private OAuthClientResolver resolver;
    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private AppleAuthClient appleAuthClient;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        resolver = mock(OAuthClientResolver.class);
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        appleAuthClient = mock(AppleAuthClient.class);
        JwtProvider jwtProvider = mock(JwtProvider.class);
        when(jwtProvider.createAccessToken(any())).thenReturn("access");
        when(jwtProvider.createRefreshToken(any())).thenReturn("refresh");

        OAuthClient appleClient = mock(OAuthClient.class);
        when(appleClient.getUserInfo(any())).thenReturn(new OAuthUserInfo("apple-uid", "최예진"));
        when(resolver.resolve(AuthProvider.APPLE)).thenReturn(appleClient);

        authService = new AuthService(resolver, userRepository, jwtProvider,
                refreshTokenRepository, appleAuthClient, 2592000000L);
    }

    @Test
    void 애플로그인_code있으면_교환후_토큰저장() {
        User user = User.builder().provider(AuthProvider.APPLE).providerId("apple-uid").name("최예진").build();
        when(userRepository.findByProviderAndProviderId(AuthProvider.APPLE, "apple-uid"))
                .thenReturn(Optional.of(user));
        when(appleAuthClient.exchangeCode("CODE")).thenReturn("RT-1");

        authService.login(AuthProvider.APPLE, new SocialLoginRequest("idtoken"), "CODE");

        verify(appleAuthClient).exchangeCode("CODE");
        org.assertj.core.api.Assertions.assertThat(user.getAppleRefreshToken()).isEqualTo("RT-1");
    }

    @Test
    void 애플로그인_교환실패해도_로그인성공() {
        User user = User.builder().provider(AuthProvider.APPLE).providerId("apple-uid").name("최예진").build();
        when(userRepository.findByProviderAndProviderId(AuthProvider.APPLE, "apple-uid"))
                .thenReturn(Optional.of(user));
        when(appleAuthClient.exchangeCode(any()))
                .thenThrow(new com.app.backend.global.exception.CustomException(
                        com.app.backend.global.exception.ErrorCode.APPLE_TOKEN_EXCHANGE_FAILED));

        AuthResponse res = authService.login(AuthProvider.APPLE, new SocialLoginRequest("idtoken"), "CODE");

        org.assertj.core.api.Assertions.assertThat(res).isNotNull();
        org.assertj.core.api.Assertions.assertThat(user.getAppleRefreshToken()).isNull();
    }

    @Test
    void 카카오로그인_code없으면_애플교환_미호출() {
        OAuthClient kakao = mock(OAuthClient.class);
        when(kakao.getUserInfo(any())).thenReturn(new OAuthUserInfo("kakao-uid", "최예진"));
        when(resolver.resolve(AuthProvider.KAKAO)).thenReturn(kakao);
        User user = User.builder().provider(AuthProvider.KAKAO).providerId("kakao-uid").name("최예진").build();
        when(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "kakao-uid"))
                .thenReturn(Optional.of(user));

        authService.login(AuthProvider.KAKAO, new SocialLoginRequest("t"), null);

        verify(appleAuthClient, never()).exchangeCode(any());
    }
}
