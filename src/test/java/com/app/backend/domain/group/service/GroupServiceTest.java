package com.app.backend.domain.group.service;

import com.app.backend.domain.cycle.repository.CycleRepository;
import com.app.backend.domain.group.entity.Group;
import com.app.backend.domain.group.repository.GroupRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.notification.service.NotificationService;
import com.app.backend.domain.shot.repository.ShotRepository;
import com.app.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CycleRepository cycleRepository;

    @Mock
    private ShotRepository shotRepository;

    @Mock
    private InviteCodeGenerator inviteCodeGenerator;

    @Mock
    private NotificationService notificationService;

    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(
                groupRepository, membershipRepository, userRepository,
                cycleRepository, shotRepository, inviteCodeGenerator, notificationService);
    }

    @Test
    void 모임_합류에_성공하면_기존_멤버에게_합류_알림을_생성한다() {
        // given: 초대코드로 찾은 모임, 5번 유저는 처음 합류
        Group group = Group.builder().name("마라탕 모임").inviteCode("ABC123").build();
        given(groupRepository.findByInviteCodeAndDeletedAtIsNull("ABC123"))
                .willReturn(Optional.of(group));
        given(membershipRepository.findByGroupIdAndUserId(group.getId(), 5L))
                .willReturn(Optional.empty());
        // 인원/모임개수 카운트, 닉네임 중복은 mock 기본값(0, false)이라 통과

        // when
        groupService.joinGroup(5L, "ABC123", "새멤버");

        // then: 합류 알림 생성 호출 (합류 본인 제외 로직은 NotificationService 내부 책임)
        verify(notificationService)
                .createMemberJoin(group.getId(), "마라탕 모임", "새멤버", 5L);
    }
}
