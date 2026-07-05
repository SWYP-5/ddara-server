package com.app.backend.domain.auth.service;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AuthService {

    private final OAuthClientResolver oAuthClientResolver;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenExpiration;

    public AuthService(OAuthClientResolver oAuthClientResolver,
                       UserRepository userRepository,
                       JwtProvider jwtProvider,
                       RefreshTokenRepository refreshTokenRepository,
                       @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.oAuthClientResolver = oAuthClientResolver;
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    @Transactional
    public AuthResponse login(AuthProvider provider, SocialLoginRequest request) {
        OAuthUserInfo userInfo = oAuthClientResolver.resolve(provider).getUserInfo(request.accessToken());

        Optional<User> found =
                userRepository.findByProviderAndProviderId(provider, userInfo.providerId());

        // 탈퇴한 계정은 탈퇴 시 provider_id를 비워두므로 여기서 조회되지 않는다 → 자연히 미가입(재가입=새 계정)
        if (found.isEmpty()) {
            return AuthResponse.signupRequired();
        }

        return issueTokens(found.get(), false);
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        OAuthUserInfo userInfo = oAuthClientResolver.resolve(request.provider()).getUserInfo(request.accessToken());

        Optional<User> found =
                userRepository.findByProviderAndProviderId(request.provider(), userInfo.providerId());
        if (found.isPresent()) {
            return issueTokens(found.get(), true);
        }

        // 신규가입(탈퇴 후 재가입 포함). 탈퇴 계정은 provider_id를 비워둬 UNIQUE 충돌 없이 새 계정 생성.
        User user = userRepository.save(User.builder()
                .provider(request.provider())
                .providerId(userInfo.providerId())
                .name(userInfo.name())
                .build());

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