package com.app.backend.domain.notification.service;

import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.entity.MembershipRole;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.notification.dto.NotificationListResponse;
import com.app.backend.domain.notification.entity.Notification;
import com.app.backend.domain.notification.entity.NotificationType;
import com.app.backend.domain.notification.repository.NotificationRepository;
import com.app.backend.domain.shot.repository.ShotRepository;
import com.app.backend.domain.user.entity.AuthProvider;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ShotRepository shotRepository;

    @Captor
    private ArgumentCaptor<Collection<NotificationType>> typesCaptor;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository, objectMapper,
                membershipRepository, userRepository, shotRepository);
    }

    // 지정한 알림 설정(prefs JSON, null이면 전체 on)을 가진 유저
    private User userWithPrefs(String prefsJson) {
        User user = User.builder()
                .provider(AuthProvider.KAKAO)
                .providerId("p")
                .name("멤버")
                .build();
        if (prefsJson != null) {
            user.updateNotificationPrefs(prefsJson);
        }
        return user;
    }

    private Membership member(Long groupId, Long userId) {
        return Membership.builder()
                .groupId(groupId)
                .userId(userId)
                .nickname("멤버" + userId)
                .role(MembershipRole.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    private Notification memberJoinNotification() {
        return Notification.builder()
                .userId(1L)
                .type(NotificationType.MEMBER_JOIN)
                .payload("{\"groupId\":7,\"groupName\":\"마라탕 맛있게 먹기\",\"actorNickname\":\"지원\"}")
                .build();
    }

    @Test
    void 알림목록을_조회하면_아이템과_안읽음수를_반환한다() {
        // given
        given(notificationRepository.findByUserIdAndTypeInOrderByCreatedAtDesc(
                eq(1L), any(), any(Pageable.class)))
                .willReturn(List.of(memberJoinNotification()));
        given(notificationRepository.countByUserIdAndReadAtIsNull(1L)).willReturn(3L);

        // when
        NotificationListResponse response = notificationService.getNotifications(1L, "all", 30);

        // then
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).type()).isEqualTo("MEMBER_JOIN");
        // payload는 문자열이 아니라 JSON 객체로 매핑됨
        assertThat(response.items().get(0).payload()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) response.items().get(0).payload()).get("groupName"))
                .isEqualTo("마라탕 맛있게 먹기");
        assertThat(response.unreadCount()).isEqualTo(3L);
    }

    @Test
    void category가_activity면_활동_타입들로_필터해_조회한다() {
        // given
        given(notificationRepository.findByUserIdAndTypeInOrderByCreatedAtDesc(
                eq(1L), typesCaptor.capture(), any(Pageable.class)))
                .willReturn(List.of());
        given(notificationRepository.countByUserIdAndReadAtIsNull(1L)).willReturn(0L);

        // when
        notificationService.getNotifications(1L, "activity", 30);

        // then
        assertThat(typesCaptor.getValue())
                .containsExactlyInAnyOrder(
                        NotificationType.NEW_CYCLE,
                        NotificationType.CYCLE_COMPLETED,
                        NotificationType.DEADLINE)
                .doesNotContain(NotificationType.MEMBER_JOIN);
    }

    @Test
    void category가_etc면_MEMBER_JOIN만_필터해_조회한다() {
        // given
        given(notificationRepository.findByUserIdAndTypeInOrderByCreatedAtDesc(
                eq(1L), typesCaptor.capture(), any(Pageable.class)))
                .willReturn(List.of());
        given(notificationRepository.countByUserIdAndReadAtIsNull(1L)).willReturn(0L);

        // when
        notificationService.getNotifications(1L, "etc", 30);

        // then
        assertThat(typesCaptor.getValue()).containsExactly(NotificationType.MEMBER_JOIN);
    }

    @Test
    void 알림_1건을_읽음처리하면_readAt이_설정된다() {
        // given: 내(1번) 안읽음 알림
        Notification n = memberJoinNotification();
        given(notificationRepository.findById(10L)).willReturn(Optional.of(n));

        // when
        notificationService.markAsRead(1L, 10L);

        // then
        assertThat(n.getReadAt()).isNotNull();
    }

    @Test
    void 이미_읽은_알림은_그대로_둔다_멱등() {
        // given: 이미 읽은 알림 (readAt 고정)
        Notification n = memberJoinNotification();
        LocalDateTime readAt = LocalDateTime.of(2026, 6, 30, 8, 0, 0);
        n.markAsRead(readAt);
        given(notificationRepository.findById(10L)).willReturn(Optional.of(n));

        // when
        notificationService.markAsRead(1L, 10L);

        // then: 처음 읽은 시각이 유지됨
        assertThat(n.getReadAt()).isEqualTo(readAt);
    }

    @Test
    void 내_알림이_아니면_NOTIFICATION_FORBIDDEN_예외를_던진다() {
        // given: 2번 유저의 알림
        Notification n = Notification.builder()
                .userId(2L).type(NotificationType.MEMBER_JOIN).payload("{}").build();
        given(notificationRepository.findById(10L)).willReturn(Optional.of(n));

        // when & then
        assertThatThrownBy(() -> notificationService.markAsRead(1L, 10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOTIFICATION_FORBIDDEN);
    }

    @Test
    void 읽음처리시_알림이_없으면_NOTIFICATION_NOT_FOUND_예외를_던진다() {
        // given
        given(notificationRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> notificationService.markAsRead(1L, 999L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    void 전체읽음하면_내_안읽음을_일괄_읽음처리한다() {
        // when
        notificationService.markAllAsRead(1L);

        // then: 내 안읽음 전부 now로 일괄 update 호출
        verify(notificationRepository).markAllAsRead(eq(1L), any(LocalDateTime.class));
    }

    // ===== 알림 생성(create) 테스트 =====

    @Test
    void 회차시작_알림은_모임_멤버_전원에게_생성된다() {
        // given: 모임 7에 멤버 1,2 (둘 다 설정 기본=전체 on)
        given(membershipRepository.findByGroupIdAndLeftAtIsNull(7L))
                .willReturn(List.of(member(7L, 1L), member(7L, 2L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithPrefs(null)));
        given(userRepository.findById(2L)).willReturn(Optional.of(userWithPrefs(null)));

        // when
        notificationService.createNewCycle(7L, "마라탕 모임", 55L);

        // then: 멤버 2명 각각 NEW_CYCLE 알림 저장
        verify(notificationRepository, org.mockito.Mockito.times(2)).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getAllValues())
                .extracting(Notification::getUserId)
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(notificationCaptor.getAllValues())
                .allMatch(n -> n.getType() == NotificationType.NEW_CYCLE);
    }

    @Test
    void 친구합류_알림은_합류자_본인을_제외한_멤버에게만_생성된다() {
        // given: 모임 7에 멤버 1,2,3 — 3번이 방금 합류한 본인
        given(membershipRepository.findByGroupIdAndLeftAtIsNull(7L))
                .willReturn(List.of(member(7L, 1L), member(7L, 2L), member(7L, 3L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithPrefs(null)));
        given(userRepository.findById(2L)).willReturn(Optional.of(userWithPrefs(null)));

        // when
        notificationService.createMemberJoin(7L, "마라탕 모임", "지원", 3L);

        // then: 1,2번에게만 생성 (3번 제외)
        verify(notificationRepository, org.mockito.Mockito.times(2)).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getAllValues())
                .extracting(Notification::getUserId)
                .containsExactlyInAnyOrder(1L, 2L)
                .doesNotContain(3L);
        // payload에 actorNickname 포함
        assertThat(notificationCaptor.getAllValues().get(0).getPayload()).contains("\"actorNickname\":\"지원\"");
    }

    @Test
    void 마감_알림은_인증샷을_안올린_미참여_멤버에게만_생성된다() {
        // given: 모임 7에 멤버 1,2 — 1번은 이미 shot 올림(참여), 2번은 미참여
        given(membershipRepository.findByGroupIdAndLeftAtIsNull(7L))
                .willReturn(List.of(member(7L, 1L), member(7L, 2L)));
        given(shotRepository.existsByCycleIdAndUserIdAndDeletedAtIsNull(55L, 1L)).willReturn(true);
        given(shotRepository.existsByCycleIdAndUserIdAndDeletedAtIsNull(55L, 2L)).willReturn(false);
        given(userRepository.findById(2L)).willReturn(Optional.of(userWithPrefs(null)));

        // when
        notificationService.createDeadline(7L, "마라탕 모임", 55L, LocalDateTime.now());

        // then: 미참여 2번에게만 생성
        verify(notificationRepository, org.mockito.Mockito.times(1)).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getUserId()).isEqualTo(2L);
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.DEADLINE);
    }

    @Test
    void 마스터_알림설정이_꺼져있으면_알림을_생성하지_않는다() {
        // given: 멤버 1번이 마스터(allowAll) off
        given(membershipRepository.findByGroupIdAndLeftAtIsNull(7L))
                .willReturn(List.of(member(7L, 1L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithPrefs(
                "{\"allowAll\":false,\"activity\":{\"followShot\":true,\"deadlineVote\":true},\"etc\":{\"memberJoin\":true}}")));

        // when
        notificationService.createNewCycle(7L, "마라탕 모임", 55L);

        // then: 저장 안 됨
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void 해당_타입_토글이_꺼져있으면_그_알림은_생성하지_않는다() {
        // given: 멤버 1번이 followShot(따라찍기) off — NEW_CYCLE 대상 토글
        given(membershipRepository.findByGroupIdAndLeftAtIsNull(7L))
                .willReturn(List.of(member(7L, 1L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithPrefs(
                "{\"allowAll\":true,\"activity\":{\"followShot\":false,\"deadlineVote\":true},\"etc\":{\"memberJoin\":true}}")));

        // when
        notificationService.createNewCycle(7L, "마라탕 모임", 55L);

        // then: followShot off라 생성 안 됨
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void 알림이_없으면_빈_목록과_0을_반환한다() {
        // given
        given(notificationRepository.findByUserIdAndTypeInOrderByCreatedAtDesc(
                eq(1L), any(), any(Pageable.class)))
                .willReturn(List.of());
        given(notificationRepository.countByUserIdAndReadAtIsNull(1L)).willReturn(0L);

        // when
        NotificationListResponse response = notificationService.getNotifications(1L, "all", 30);

        // then
        assertThat(response.items()).isEmpty();
        assertThat(response.unreadCount()).isZero();
    }
}
