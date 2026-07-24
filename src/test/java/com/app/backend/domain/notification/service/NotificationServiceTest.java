package com.app.backend.domain.notification.service;

import com.app.backend.domain.block.repository.BlockRepository;
import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.entity.MembershipRole;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.notification.dto.NotificationListResponse;
import com.app.backend.domain.notification.entity.Notification;
import com.app.backend.domain.notification.entity.NotificationType;
import com.app.backend.domain.notification.repository.NotificationRepository;
import com.app.backend.domain.shot.entity.Shot;
import com.app.backend.domain.shot.entity.ShotType;
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

    @Mock
    private BlockRepository blockRepository;

    @Mock
    private FcmService fcmService;

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
                membershipRepository, userRepository, shotRepository, blockRepository, fcmService);
    }

    // 회차의 스타터 원본 가이드샷 (imageUrl 지정)
    private Shot starterShot(String imageUrl) {
        return Shot.builder()
                .cycleId(55L)
                .userId(9L)
                .type(ShotType.STARTER)
                .imageUrl(imageUrl)
                .build();
    }

    private static final LocalDateTime DEADLINE_AT = LocalDateTime.of(2026, 7, 6, 21, 0);

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
                        NotificationType.DEADLINE,
                        NotificationType.STARTER_ASSIGNED)
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
        notificationService.createNewCycle(7L, "마라탕 모임", 55L, 3L, DEADLINE_AT);

        // then: 멤버 2명 각각 NEW_CYCLE 알림 저장
        verify(notificationRepository, org.mockito.Mockito.times(2)).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getAllValues())
                .extracting(Notification::getUserId)
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(notificationCaptor.getAllValues())
                .allMatch(n -> n.getType() == NotificationType.NEW_CYCLE);
    }

    @Test
    void 스타터지정_알림은_멤버_전원에게_생성되고_스타터를_노출하지_않는다() {
        // given: 모임 7에 멤버 1,2,3 (3번이 지정된 스타터)
        given(membershipRepository.findByGroupIdAndLeftAtIsNull(7L))
                .willReturn(List.of(member(7L, 1L), member(7L, 2L), member(7L, 3L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithPrefs(null)));
        given(userRepository.findById(2L)).willReturn(Optional.of(userWithPrefs(null)));
        given(userRepository.findById(3L)).willReturn(Optional.of(userWithPrefs(null)));

        // when
        notificationService.createStarterAssigned(7L, "마라탕 모임");

        // then: 지정된 스타터 본인 포함 3명 전원에게 생성
        verify(notificationRepository, org.mockito.Mockito.times(3)).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getAllValues())
                .extracting(Notification::getUserId)
                .containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(notificationCaptor.getAllValues())
                .allMatch(n -> n.getType() == NotificationType.STARTER_ASSIGNED);
        assertThat(notificationCaptor.getAllValues().get(0).getPayload())
                .contains("\"imageUrl\":null")
                .doesNotContain("starterUserId", "nickname");
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
        notificationService.createDeadline(7L, "마라탕 모임", 55L, LocalDateTime.now(), 60);

        // then: 미참여 2번에게만 생성
        verify(notificationRepository, org.mockito.Mockito.times(1)).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getUserId()).isEqualTo(2L);
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.DEADLINE);
    }

    @Test
    void 푸시_문구는_디자인_확정_문구를_따른다() {
        // given: 모임 7에 멤버 1 (설정 전체 on, 미참여)
        User user = userWithPrefs(null);
        given(membershipRepository.findByGroupIdAndLeftAtIsNull(7L))
                .willReturn(List.of(member(7L, 1L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when: 4종 알림 생성
        notificationService.createNewCycle(7L, "마라탕 모임", 55L, 3L, DEADLINE_AT);
        notificationService.createCycleCompleted(7L, "마라탕 모임", 55L);
        notificationService.createMemberJoin(7L, "마라탕 모임", "지원", 3L);
        notificationService.createDeadline(7L, "마라탕 모임", 55L, LocalDateTime.now(), 60);

        // then: 디자인(피그마 알림 리스트) 확정 문구 그대로
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(fcmService, org.mockito.Mockito.times(4)).sendTo(
                eq(user), title.capture(), body.capture(), org.mockito.ArgumentMatchers.anyMap());
        assertThat(title.getAllValues()).containsExactly(
                "새 따라찍기 시작", "따라찍기 완료", "모임 참여", "마감 임박");
        assertThat(body.getAllValues()).containsExactly(
                "'마라탕 모임'에서 새 따라찍기가 시작됐어요!",
                "'마라탕 모임'에서 따라찍기가 완료되었어요!",
                "지원님이 '마라탕 모임' 모임에 합류했어요",
                "'마라탕 모임' 따라찍기가 1시간 후 마감돼요. 아직 안찍었죠?");
    }

    @Test
    void 마감_알림은_같은_회차에_이미_생성했으면_중복_생성하지_않는다() {
        // given: 55번 회차의 DEADLINE 알림이 이미 존재 (스케줄러가 1분마다 재호출하는 상황)
        given(notificationRepository.existsDeadlineNotification(
                NotificationType.DEADLINE.name(), 55L, 60)).willReturn(true);

        // when
        notificationService.createDeadline(7L, "마라탕 모임", 55L, LocalDateTime.now(), 60);

        // then: 수신자 계산도, 저장도 하지 않고 스킵
        verify(notificationRepository, never()).save(any());
        verify(membershipRepository, never()).findByGroupIdAndLeftAtIsNull(any());
    }

    @Test
    void 마스터_알림설정이_꺼져있어도_인앱은_저장하고_푸시만_막는다() {
        // given: 멤버 1번이 마스터(allowAll) off
        given(membershipRepository.findByGroupIdAndLeftAtIsNull(7L))
                .willReturn(List.of(member(7L, 1L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithPrefs(
                "{\"allowAll\":false,\"activity\":{\"followShot\":true,\"deadlineVote\":true},\"etc\":{\"memberJoin\":true}}")));

        // when
        notificationService.createNewCycle(7L, "마라탕 모임", 55L, 3L, DEADLINE_AT);

        // then: 인앱 알림은 저장(목록엔 뜸), 푸시는 미발송
        verify(notificationRepository).save(any());
        verify(fcmService, never()).sendTo(any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void 해당_타입_토글이_꺼져있어도_인앱은_저장하고_푸시만_막는다() {
        // given: 멤버 1번이 followShot(따라찍기) off — NEW_CYCLE 대상 토글
        given(membershipRepository.findByGroupIdAndLeftAtIsNull(7L))
                .willReturn(List.of(member(7L, 1L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithPrefs(
                "{\"allowAll\":true,\"activity\":{\"followShot\":false,\"deadlineVote\":true},\"etc\":{\"memberJoin\":true}}")));

        // when
        notificationService.createNewCycle(7L, "마라탕 모임", 55L, 3L, DEADLINE_AT);

        // then: followShot off여도 인앱 저장, 푸시만 미발송
        verify(notificationRepository).save(any());
        verify(fcmService, never()).sendTo(any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void 알림_생성시_수신자에게_FCM_푸시_발송도_요청한다() {
        // given: 모임 7에 멤버 1 (설정 전체 on)
        User user = userWithPrefs(null);
        given(membershipRepository.findByGroupIdAndLeftAtIsNull(7L))
                .willReturn(List.of(member(7L, 1L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        notificationService.createMemberJoin(7L, "마라탕 모임", "지원", 3L);

        // then: 인앱 저장 + FCM 발송 요청 둘 다 수행
        verify(notificationRepository).save(any());
        verify(fcmService).sendTo(eq(user), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap());
    }

    // ===== 알림 사진(imageUrl) 테스트 — #87·#93 =====

    private static final String STARTER_SHOT_URL =
            "https://ddara-images.s3.ap-northeast-2.amazonaws.com/shots/guide.jpg";

    @Test
    void 회차시작_알림_payload에는_스타터의_원본_가이드샷_URL이_담긴다() {
        // given: 모임 7 멤버 1(수신자), 회차 55의 스타터 원본 가이드샷 존재
        given(membershipRepository.findByGroupIdAndLeftAtIsNull(7L))
                .willReturn(List.of(member(7L, 1L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithPrefs(null)));
        given(shotRepository.findByCycleIdAndType(55L, ShotType.STARTER))
                .willReturn(Optional.of(starterShot(STARTER_SHOT_URL)));

        // when
        notificationService.createNewCycle(7L, "마라탕 모임", 55L, 3L, DEADLINE_AT);

        // then: payload.imageUrl = 스타터 원본 가이드샷 URL
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getPayload())
                .contains("\"imageUrl\":\"" + STARTER_SHOT_URL + "\"");
    }

    @Test
    void 따라찍기완료_알림_payload에도_스타터의_원본_가이드샷_URL이_담긴다() {
        given(membershipRepository.findByGroupIdAndLeftAtIsNull(7L))
                .willReturn(List.of(member(7L, 1L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithPrefs(null)));
        given(shotRepository.findByCycleIdAndType(55L, ShotType.STARTER))
                .willReturn(Optional.of(starterShot(STARTER_SHOT_URL)));

        // when
        notificationService.createCycleCompleted(7L, "마라탕 모임", 55L);

        // then
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getPayload())
                .contains("\"imageUrl\":\"" + STARTER_SHOT_URL + "\"");
    }

    @Test
    void 스타터_원본샷이_없으면_imageUrl은_null이다() {
        given(membershipRepository.findByGroupIdAndLeftAtIsNull(7L))
                .willReturn(List.of(member(7L, 1L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithPrefs(null)));
        given(shotRepository.findByCycleIdAndType(55L, ShotType.STARTER))
                .willReturn(Optional.empty());

        // when
        notificationService.createNewCycle(7L, "마라탕 모임", 55L, 3L, DEADLINE_AT);

        // then: imageUrl은 null (프론트가 기본 표시)
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getPayload()).contains("\"imageUrl\":null");
    }

    @Test
    void 회차시작_알림_payload에는_마감시각이_담긴다() {
        // given
        given(membershipRepository.findByGroupIdAndLeftAtIsNull(7L))
                .willReturn(List.of(member(7L, 1L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithPrefs(null)));
        given(shotRepository.findByCycleIdAndType(55L, ShotType.STARTER)).willReturn(Optional.empty());

        // when: 마감 2026-07-06T21:00
        notificationService.createNewCycle(7L, "마라탕 모임", 55L, 3L, LocalDateTime.of(2026, 7, 6, 21, 0));

        // then: payload에 deadlineAt 포함 (KST 오프셋 +09:00)
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getPayload()).contains("\"deadlineAt\":\"2026-07-06T21:00+09:00\"");
    }

    @Test
    void 알림목록의_readAt은_KST_오프셋이_붙어_반환된다() {
        // given: 2026-07-06 20:00에 읽은 알림
        Notification n = memberJoinNotification();
        n.markAsRead(LocalDateTime.of(2026, 7, 6, 20, 0));
        given(notificationRepository.findByUserIdAndTypeInOrderByCreatedAtDesc(
                eq(1L), any(), any(Pageable.class))).willReturn(List.of(n));
        given(notificationRepository.countByUserIdAndReadAtIsNull(1L)).willReturn(0L);

        // when
        NotificationListResponse response = notificationService.getNotifications(1L, "all", 30);

        // then: +09:00 오프셋 포함
        assertThat(response.items().get(0).readAt().toString()).isEqualTo("2026-07-06T20:00+09:00");
    }

    @Test
    void 응답_JSON_직렬화시_시각에_KST_오프셋이_찍힌다() throws Exception {
        // given: 앱과 동일한 Jackson 설정 (JavaTimeModule + ISO 문자열)
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var item = new com.app.backend.domain.notification.dto.NotificationItem(
                1L, "MEMBER_JOIN", Map.of(), null,
                java.time.OffsetDateTime.of(2026, 7, 6, 20, 0, 0, 0, java.time.ZoneOffset.ofHours(9)));

        // when
        String json = mapper.writeValueAsString(item);

        // then: createdAt에 +09:00이 붙어 나감 (프론트가 UTC로 오해 안 하도록)
        assertThat(json).contains("2026-07-06T20:00:00+09:00");
    }

    @Test
    void 모임참여_알림_payload의_imageUrl은_null이다() {
        given(membershipRepository.findByGroupIdAndLeftAtIsNull(7L))
                .willReturn(List.of(member(7L, 1L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithPrefs(null)));

        // when
        notificationService.createMemberJoin(7L, "마라탕 모임", "지원", 3L);

        // then: payload.imageUrl = null (기본 아이콘 표시용)
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getPayload())
                .contains("\"imageUrl\":null");
    }

    @Test
    void 마감_알림_payload의_imageUrl도_null이다() {
        given(membershipRepository.findByGroupIdAndLeftAtIsNull(7L))
                .willReturn(List.of(member(7L, 1L)));
        given(shotRepository.existsByCycleIdAndUserIdAndDeletedAtIsNull(55L, 1L)).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithPrefs(null)));

        // when
        notificationService.createDeadline(7L, "마라탕 모임", 55L, LocalDateTime.now(), 60);

        // then
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getPayload())
                .contains("\"imageUrl\":null");
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
