package com.app.backend.domain.user.service;

import com.app.backend.domain.auth.apple.AppleAuthClient;
import com.app.backend.domain.auth.repository.RefreshTokenRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.notification.repository.NotificationRepository;
import com.app.backend.domain.user.entity.AuthProvider;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceWithdrawTest {

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private AppleAuthClient appleAuthClient;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        appleAuthClient = mock(AppleAuthClient.class);
        userService = new UserService(userRepository, new ObjectMapper(), refreshTokenRepository,
                mock(NotificationRepository.class), mock(MembershipRepository.class),
                appleAuthClient, "ddara-images", "ap-northeast-2");
    }

    @Test
    void 비애플유저_revoke미호출() {
        User u = User.builder().provider(AuthProvider.KAKAO).providerId("uid").name("최예진").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        userService.withdraw(1L, null);
        verify(appleAuthClient, never()).revoke(any());
    }
}