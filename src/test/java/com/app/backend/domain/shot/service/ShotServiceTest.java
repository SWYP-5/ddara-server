package com.app.backend.domain.shot.service;

import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.entity.CycleStatus;
import com.app.backend.domain.cycle.repository.CycleRepository;
import com.app.backend.domain.group.entity.Group;
import com.app.backend.domain.group.repository.GroupRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.notification.service.NotificationService;
import com.app.backend.domain.shot.dto.ShotUploadRequest;
import com.app.backend.domain.shot.entity.Shot;
import com.app.backend.domain.shot.repository.ShotRepository;
import com.app.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShotServiceTest {

    @Mock
    private CycleRepository cycleRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private ShotRepository shotRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    private ShotService shotService;

    @BeforeEach
    void setUp() {
        shotService = new ShotService(
                cycleRepository, groupRepository, membershipRepository,
                shotRepository, userRepository, notificationService);
    }

    // 그룹 7, 회차 55(진행 중, 스타터=99). 업로더는 멤버 1.
    private Cycle inProgressCycle() {
        Cycle cycle = Cycle.builder()
                .groupId(7L).cycleNumber(1).topic("주제").starterUserId(99L).build();
        ReflectionTestUtils.setField(cycle, "id", 55L);
        return cycle;
    }

    private void stubUploadCommon(Cycle cycle) {
        given(cycleRepository.findById(55L)).willReturn(Optional.of(cycle));
        given(membershipRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(7L, 1L)).willReturn(true);
        given(shotRepository.findByCycleIdAndUserId(55L, 1L)).willReturn(Optional.empty());
        given(shotRepository.save(any(Shot.class))).willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 전원이_업로드하면_회차를_마감하고_마감_알림을_보낸다() {
        // given: 활성 멤버 3명, 이번 업로드로 인증샷도 3장 → 전원 완료
        Cycle cycle = inProgressCycle();
        stubUploadCommon(cycle);
        given(membershipRepository.countByGroupIdAndLeftAtIsNull(7L)).willReturn(3L);
        given(shotRepository.countByCycleIdAndDeletedAtIsNull(55L)).willReturn(3L);
        given(groupRepository.findById(7L)).willReturn(Optional.of(
                Group.builder().name("마라탕 모임").ownerUserId(99L).build()));

        // when
        shotService.uploadShot(1L, 55L, new ShotUploadRequest("https://img/1.jpg"));

        // then: 회차 마감 + 마감 알림 발송
        assertThat(cycle.getStatus()).isEqualTo(CycleStatus.DONE);
        verify(notificationService).createCycleCompleted(7L, "마라탕 모임", 55L);
    }

    @Test
    void 아직_전원이_아니면_마감_알림을_보내지_않는다() {
        // given: 활성 멤버 3명, 인증샷은 2장뿐 → 아직 미완료
        Cycle cycle = inProgressCycle();
        stubUploadCommon(cycle);
        given(membershipRepository.countByGroupIdAndLeftAtIsNull(7L)).willReturn(3L);
        given(shotRepository.countByCycleIdAndDeletedAtIsNull(55L)).willReturn(2L);

        // when
        shotService.uploadShot(1L, 55L, new ShotUploadRequest("https://img/1.jpg"));

        // then: 마감 안 됨 + 알림 없음
        assertThat(cycle.getStatus()).isEqualTo(CycleStatus.IN_PROGRESS);
        verify(notificationService, never()).createCycleCompleted(any(), any(), any());
    }
}
