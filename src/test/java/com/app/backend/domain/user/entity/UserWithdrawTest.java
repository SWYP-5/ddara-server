package com.app.backend.domain.user.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserWithdrawTest {

    private User appleUser() {
        return User.builder()
                .provider(AuthProvider.APPLE)
                .providerId("apple-uid-1")
                .name("최예진")
                .build();
    }

    @Test
    void updateAppleRefreshToken_저장하고_clear로_지운다() {
        User user = appleUser();
        user.updateAppleRefreshToken("RT-1");
        assertThat(user.getAppleRefreshToken()).isEqualTo("RT-1");

        user.clearAppleRefreshToken();
        assertThat(user.getAppleRefreshToken()).isNull();
    }

    @Test
    void withdraw시_appleRefreshToken도_비운다() {
        User user = appleUser();
        user.updateAppleRefreshToken("RT-1");

        user.withdraw(LocalDateTime.now());

        assertThat(user.getAppleRefreshToken()).isNull();
        assertThat(user.isWithdrawn()).isTrue();
    }
}
