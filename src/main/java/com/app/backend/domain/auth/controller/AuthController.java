package com.app.backend.domain.auth.controller;

import com.app.backend.domain.auth.dto.AppleLoginRequest;
import com.app.backend.domain.auth.dto.AuthResponse;
import com.app.backend.domain.auth.dto.LogoutRequest;
import com.app.backend.domain.auth.dto.RefreshRequest;
import com.app.backend.domain.auth.dto.SignupRequest;
import com.app.backend.domain.auth.dto.SocialLoginRequest;
import com.app.backend.domain.auth.dto.TokenResponse;
import com.app.backend.domain.auth.service.AuthService;
import com.app.backend.domain.user.entity.AuthProvider;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/kakao")
    public AuthResponse kakaoLogin(@Valid @RequestBody SocialLoginRequest request) {
        return authService.login(AuthProvider.KAKAO, request);
    }

    @PostMapping("/google")
    public AuthResponse googleLogin(@Valid @RequestBody SocialLoginRequest request) {
        return authService.login(AuthProvider.GOOGLE, request);
    }

    @PostMapping("/apple")
    public AuthResponse appleLogin(@Valid @RequestBody AppleLoginRequest request) {
        return authService.login(AuthProvider.APPLE, new SocialLoginRequest(request.idToken()));
    }

    @PostMapping("/signup")
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }
}
