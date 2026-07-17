package com.app.backend.domain.user.service;

import com.app.backend.domain.auth.apple.AppleAuthClient;
import com.app.backend.domain.auth.repository.RefreshTokenRepository;
import com.app.backend.domain.block.repository.BlockRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.group.service.GroupService;
import com.app.backend.domain.notification.repository.NotificationRepository;
import com.app.backend.domain.user.entity.AuthProvider;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserServiceWithdrawTest {

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private GroupService groupService;
    private AppleAuthClient appleAuthClient;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        groupService = mock(GroupService.class);
        appleAuthClient = mock(AppleAuthClient.class);
        userService = new UserService(userRepository, new ObjectMapper(), refreshTokenRepository,
                mock(NotificationRepository.class), mock(MembershipRepository.class),
                mock(BlockRepository.class),
                groupService, appleAuthClient,
                mock(com.app.backend.domain.upload.service.UploadService.class),
                "ddara-images", "ap-northeast-2");
    }

    @Test
    void 비애플유저_revoke미호출() {
        User u = User.builder().provider(AuthProvider.KAKAO).providerId("uid").name("최예진").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        userService.withdraw(1L, null);
        verify(appleAuthClient, never()).revoke(any());
    }

    @Test
    void 탈퇴하면_속한_모든_모임에서_나간것으로_처리한다() {
        User u = User.builder().provider(AuthProvider.KAKAO).providerId("uid").name("최예진").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));

        userService.withdraw(1L, null);

        // 멤버 목록·회차 참여 인원 제외 + 마지막 멤버면 모임 삭제 (모임 나가기와 동일 규칙)
        verify(groupService).leaveAllGroupsOnWithdrawal(eq(1L), any());
    }
}