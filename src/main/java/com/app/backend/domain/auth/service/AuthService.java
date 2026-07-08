package com.app.backend.domain.auth.service;

import com.app.backend.domain.auth.apple.AppleAuthClient;
import com.app.backend.domain.auth.dto.AuthResponse;
import com.app.backend.domain.auth.dto.SignupRequest;
import com.app.backend.domain.auth.dto.SocialLoginRequest;
import com.app.backend.domain.auth.dto.TokenResponse;
import com.app.backend.domain.auth.entity.RefreshToken;
import com.app.backend.domain.auth.jwt.JwtProvider;
import com.app.backend.domain.auth.oauth.OAuthClientResolver;
import com.app.backend.domain.auth.oauth.OAuthUserInfo;
import com.app.backend.domain.auth.repository.RefreshTokenRepository;
import com.app.backend.domain.user.entity.AuthProvider;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final OAuthClientResolver oAuthClientResolver;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AppleAuthClient appleAuthClient;
    private final long refreshTokenExpiration;

    public AuthService(OAuthClientResolver oAuthClientResolver,
                       UserRepository userRepository,
                       JwtProvider jwtProvider,
                       RefreshTokenRepository refreshTokenRepository,
                       AppleAuthClient appleAuthClient,
                       @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.oAuthClientResolver = oAuthClientResolver;
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.appleAuthClient = appleAuthClient;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    @Transactional
    public AuthResponse login(AuthProvider provider, SocialLoginRequest request, String appleAuthorizationCode) {
        OAuthUserInfo userInfo = oAuthClientResolver.resolve(provider).getUserInfo(request.accessToken());

        Optional<User> found =
                userRepository.findByProviderAndProviderId(provider, userInfo.providerId());

        // 탈퇴한 계정은 탈퇴 시 provider_id를 비워두므로 여기서 조회되지 않는다 → 자연히 미가입(재가입=새 계정)
        if (found.isEmpty()) {
            return AuthResponse.signupRequired();
        }

        User user = found.get();
        storeAppleRefreshTokenIfPresent(provider, user, appleAuthorizationCode);
        return issueTokens(user, false);
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        OAuthUserInfo userInfo = oAuthClientResolver.resolve(request.provider()).getUserInfo(request.accessToken());

        Optional<User> found =
                userRepository.findByProviderAndProviderId(request.provider(), userInfo.providerId());
        if (found.isPresent()) {
            User user = found.get();
            storeAppleRefreshTokenIfPresent(request.provider(), user, request.appleAuthorizationCode());
            return issueTokens(user, true);
        }

        // 신규가입(탈퇴 후 재가입 포함). 탈퇴 계정은 provider_id를 비워둬 UNIQUE 충돌 없이 새 계정 생성.
        // 애플은 최초 연동 1회만 이름을 제공하므로 재가입 시 토큰에 이름이 없을 수 있음 -> 임시 이름으로 가입
        String name = (userInfo.name() == null || userInfo.name().isBlank()) ? "사용자" : userInfo.name();
        User user = userRepository.save(User.builder()
                .provider(request.provider())
                .providerId(userInfo.providerId())
                .name(name)
                .build());

        storeAppleRefreshTokenIfPresent(request.provider(), user, request.appleAuthorizationCode());
        return issueTokens(user, true);
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        RefreshToken saved = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REFRESH_TOKEN));

        String newAccessToken = jwtProvider.createAccessToken(saved.getUserId());
        return new TokenResponse(newAccessToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        // 로그아웃한 기기로 푸시가 가지 않도록 FCM 토큰도 함께 제거 (#77)
        refreshTokenRepository.findByToken(refreshToken).ifPresent(saved ->
                userRepository.findById(saved.getUserId()).ifPresent(User::clearFcmToken));
        refreshTokenRepository.deleteByToken(refreshToken);
    }

    // 애플 로그인이고 authorizationCode가 있으면 refresh_token으로 교환해 저장한다.
    // 교환 실패는 로그인/가입을 막지 않도록 삼키고 경고만 남긴다(연동 해제용 토큰만 미확보).
    private void storeAppleRefreshTokenIfPresent(AuthProvider provider, User user, String appleAuthorizationCode) {
        if (provider != AuthProvider.APPLE || appleAuthorizationCode == null || appleAuthorizationCode.isBlank()) {
            return;
        }
        try {
            String refreshToken = appleAuthClient.exchangeCode(appleAuthorizationCode);
            user.updateAppleRefreshToken(refreshToken);
        } catch (Exception e) {
            log.warn("애플 refresh_token 확보 실패(로그인은 계속 진행): userId={}", user.getId(), e);
        }
    }

    private AuthResponse issueTokens(User user, boolean isNewUser) {
        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        refreshTokenRepository.save(RefreshToken.builder()
                .token(refreshToken)
                .userId(user.getId())
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000))
                .build());

        return isNewUser
                ? AuthResponse.signup(accessToken, refreshToken, user)
                : AuthResponse.login(accessToken, refreshToken, user);
    }
}