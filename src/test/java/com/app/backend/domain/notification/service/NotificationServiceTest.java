package com.app.backend.domain.notification.service;

import com.app.backend.domain.notification.dto.NotificationListResponse;
import com.app.backend.domain.notification.entity.Notification;
import com.app.backend.domain.notification.entity.NotificationType;
import com.app.backend.domain.notification.repository.NotificationRepository;
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

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Captor
    private ArgumentCaptor<Collection<NotificationType>> typesCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, objectMapper);
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
