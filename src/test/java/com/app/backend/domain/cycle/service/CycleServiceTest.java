package com.app.backend.domain.cycle.service;

import com.app.backend.domain.cycle.dto.CreateCycleRequest;
import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.entity.CycleStatus;
import com.app.backend.domain.cycle.repository.CycleRepository;
import com.app.backend.domain.group.entity.Group;
import com.app.backend.domain.group.repository.GroupRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.notification.service.NotificationService;
import com.app.backend.domain.shot.repository.ShotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CycleServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private CycleRepository cycleRepository;

    @Mock
    private ShotRepository shotRepository;

    @Mock
    private NotificationService notificationService;

    private CycleService cycleService;

    @BeforeEach
    void setUp() {
        cycleService = new CycleService(
                groupRepository, membershipRepository, cycleRepository,
                shotRepository, notificationService);
    }

    private Group group(String name) {
        return Group.builder().name(name).build();
    }

    private Cycle cycle(Long groupId, LocalDateTime deadlineAt) {
        return Cycle.builder()
                .groupId(groupId)
                .cycleNumber(1)
                .topic("주제")
                .starterUserId(1L)
                .startedAt(deadlineAt.minusHours(24))
                .deadlineAt(deadlineAt)
                .build();
    }

    @Test
    void 회차를_시작하면_모임_멤버에게_새_회차_알림을_생성한다() {
        // given: 모임 7, 멤버 3명, 진행 중 회차 없음
        given(groupRepository.findById(7L)).willReturn(Optional.of(group("마라탕 모임")));
        given(membershipRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(7L, 1L)).willReturn(true);
        given(membershipRepository.countByGroupIdAndLeftAtIsNull(7L)).willReturn(3L);
        given(cycleRepository.existsByGroupIdAndStatus(7L, CycleStatus.IN_PROGRESS)).willReturn(false);
        given(cycleRepository.countByGroupId(7L)).willReturn(0L);
        given(cycleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(shotRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        cycleService.createCycle(1L, 7L, new CreateCycleRequest("점프샷", "https://img.example/1.jpg"));

        // then
        verify(notificationService).createNewCycle(eq(7L), eq("마라탕 모임"), any(), any(), any());
    }

    @Test
    void 기한이_지난_회차를_마감하면_마감_알림을_생성한다() {
        // given: 24h 지난 진행 중 회차 1건
        Cycle overdue = cycle(7L, LocalDateTime.now().minusMinutes(1));
        given(cycleRepository.findByStatusAndDeadlineAtBefore(eq(CycleStatus.IN_PROGRESS), any()))
                .willReturn(List.of(overdue));
        given(groupRepository.findById(7L)).willReturn(Optional.of(group("마라탕 모임")));

        // when
        int closed = cycleService.closeOverdueCycles();

        // then: 마감 처리 + 알림 생성
        assertThat(closed).isEqualTo(1);
        assertThat(overdue.getStatus()).isEqualTo(CycleStatus.DONE);
        verify(notificationService).createCycleCompleted(eq(7L), eq("마라탕 모임"), any());
    }

    @Test
    void 마감_45분_전이면_60분_단계_알림만_생성한다() {
        // given: 마감까지 45분 남은 진행 중 회차 (60분 단계 대상, 30/5/1분은 아직)
        Cycle closing = cycle(7L, LocalDateTime.now().plusMinutes(45));
        given(cycleRepository.findByStatusAndDeadlineAtBetween(
                eq(CycleStatus.IN_PROGRESS), any(), any()))
                .willReturn(List.of(closing));
        given(groupRepository.findById(7L)).willReturn(Optional.of(group("마라탕 모임")));

        // when
        cycleService.notifyUpcomingDeadlines();

        // then: 60분 단계만 발송, 30분 단계는 아직 아님
        verify(notificationService)
                .createDeadline(eq(7L), eq("마라탕 모임"), any(), eq(closing.getDeadlineAt()), eq(60));
        verify(notificationService, org.mockito.Mockito.never())
                .createDeadline(any(), any(), any(), any(), eq(30));
    }
}
