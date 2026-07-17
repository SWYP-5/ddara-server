package com.app.backend.domain.group.service;

import com.app.backend.domain.cycle.repository.CycleRepository;
import com.app.backend.domain.group.entity.Group;
import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.entity.MembershipRole;
import com.app.backend.domain.group.repository.GroupRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.notification.service.NotificationService;
import com.app.backend.domain.shot.repository.ShotRepository;
import com.app.backend.domain.upload.service.UploadService;
import com.app.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Mock
    private UploadService uploadService;

    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(
                groupRepository, membershipRepository, userRepository,
                cycleRepository, shotRepository, inviteCodeGenerator, notificationService,
                uploadService);
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

    private Membership activeMembership(long groupId, long userId) {
        return Membership.builder()
                .groupId(groupId)
                .userId(userId)
                .nickname("닉")
                .role(MembershipRole.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void 탈퇴하면_속한_모임에서_나간것으로_처리하고_마지막_멤버면_모임을_삭제한다() {
        // given: 7번 유저가 100번 모임의 활성 멤버(그리고 이 모임의 마지막 멤버)
        Membership membership = activeMembership(100L, 7L);
        given(membershipRepository.findByUserIdAndLeftAtIsNull(7L))
                .willReturn(List.of(membership));
        Group group = Group.builder().name("따라 모임").build();
        given(groupRepository.findById(100L)).willReturn(Optional.of(group));
        // 나간 뒤 활성 멤버 0명 → 모임 삭제 대상
        given(membershipRepository.countByGroupIdAndLeftAtIsNull(100L)).willReturn(0L);

        // when
        groupService.leaveAllGroupsOnWithdrawal(7L, LocalDateTime.now());

        // then: 멤버십에 나간시각이 찍히고 모임은 soft delete 된다
        assertThat(membership.getLeftAt()).isNotNull();
        assertThat(group.getDeletedAt()).isNotNull();
    }

    @Test
    void 탈퇴해도_남은_활성_멤버가_있으면_모임을_삭제하지_않는다() {
        // given: 7번 유저가 100번 모임의 활성 멤버(하지만 다른 멤버가 남아있음)
        Membership membership = activeMembership(100L, 7L);
        given(membershipRepository.findByUserIdAndLeftAtIsNull(7L))
                .willReturn(List.of(membership));
        // 나간 뒤에도 활성 멤버 2명 → 모임 유지
        given(membershipRepository.countByGroupIdAndLeftAtIsNull(100L)).willReturn(2L);

        // when
        groupService.leaveAllGroupsOnWithdrawal(7L, LocalDateTime.now());

        // then: 나간시각은 찍히지만 모임 삭제 조회조차 하지 않는다
        assertThat(membership.getLeftAt()).isNotNull();
        verify(groupRepository, org.mockito.Mockito.never()).findById(100L);
    }
}
