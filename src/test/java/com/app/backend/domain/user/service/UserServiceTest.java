package com.app.backend.domain.user.service;

import com.app.backend.domain.user.dto.UserInfoResponse;
import com.app.backend.domain.user.entity.AuthProvider;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void 내_정보를_조회하면_본인_프로필_정보를_반환한다() {
        // given: id 1번 유저가 DB에 있다고 가정
        User user = User.builder()
                .provider(AuthProvider.KAKAO)
                .providerId("kakao-123")
                .email("minju@kakao.com")
                .nickname("민주")
                .birthDate(LocalDate.of(2005, 3, 14))
                .profileImageUrl(null)
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        UserInfoResponse response = userService.getMyInfo(1L);

        // then
        assertThat(response.nickname()).isEqualTo("민주");
        assertThat(response.email()).isEqualTo("minju@kakao.com");
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
}