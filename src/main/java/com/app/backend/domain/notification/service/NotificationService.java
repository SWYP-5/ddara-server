package com.app.backend.domain.notification.service;

import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.notification.dto.NotificationItem;
import com.app.backend.domain.notification.dto.NotificationListResponse;
import com.app.backend.domain.notification.entity.Notification;
import com.app.backend.domain.notification.entity.NotificationType;
import com.app.backend.domain.notification.repository.NotificationRepository;
import com.app.backend.domain.shot.repository.ShotRepository;
import com.app.backend.domain.user.dto.NotificationSettingsResponse;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    // category → 포함할 type 집합
    private static final Collection<NotificationType> ACTIVITY_TYPES = EnumSet.of(
            NotificationType.NEW_CYCLE, NotificationType.CYCLE_COMPLETED, NotificationType.DEADLINE);
    private static final Collection<NotificationType> ETC_TYPES = EnumSet.of(
            NotificationType.MEMBER_JOIN);

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final ShotRepository shotRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               ObjectMapper objectMapper,
                               MembershipRepository membershipRepository,
                               UserRepository userRepository,
                               ShotRepository shotRepository) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.shotRepository = shotRepository;
    }

    // ===== 알림 생성(INSERT) 내부 인터페이스 — 회차/모임 흐름(오지원)에서 호출 (부록 B) =====
    // 각 메서드: 수신자별 notification_prefs 확인 → 켜져 있으면 인앱 알림 저장.
    // FCM 발송은 Firebase 프로젝트 생성 후 추가 예정(현재 스킵, notifyEach의 TODO 참고).

    /** 회차 시작 → 모임 멤버 전원. */
    @Transactional
    public void createNewCycle(Long groupId, String groupName, Long cycleId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("groupId", groupId);
        payload.put("groupName", groupName);
        payload.put("cycleId", cycleId);
        notifyEach(activeMemberIds(groupId), NotificationType.NEW_CYCLE, payload);
    }

    /** 회차 마감 → 모임 멤버 전원. */
    @Transactional
    public void createCycleCompleted(Long groupId, String groupName, Long cycleId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("groupId", groupId);
        payload.put("groupName", groupName);
        payload.put("cycleId", cycleId);
        notifyEach(activeMemberIds(groupId), NotificationType.CYCLE_COMPLETED, payload);
    }

    /** 모임 합류 → 합류자 본인 제외 멤버 전원. */
    @Transactional
    public void createMemberJoin(Long groupId, String groupName, String actorNickname, Long joinedUserId) {
        List<Long> recipients = activeMemberIds(groupId).stream()
                .filter(id -> !id.equals(joinedUserId))
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("groupId", groupId);
        payload.put("groupName", groupName);
        payload.put("actorNickname", actorNickname);
        notifyEach(recipients, NotificationType.MEMBER_JOIN, payload);
    }

    /** 마감 1시간 전(타이밍은 오지원 스케줄러) → 아직 인증샷을 올리지 않은 미참여 멤버. */
    @Transactional
    public void createDeadline(Long groupId, String groupName, Long cycleId, LocalDateTime deadlineAt) {
        List<Long> recipients = activeMemberIds(groupId).stream()
                .filter(id -> !shotRepository.existsByCycleIdAndUserIdAndDeletedAtIsNull(cycleId, id))
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("groupId", groupId);
        payload.put("groupName", groupName);
        payload.put("cycleId", cycleId);
        payload.put("deadlineAt", deadlineAt.toString());
        notifyEach(recipients, NotificationType.DEADLINE, payload);
    }

    private List<Long> activeMemberIds(Long groupId) {
        return membershipRepository.findByGroupIdAndLeftAtIsNull(groupId).stream()
                .map(Membership::getUserId)
                .toList();
    }

    /** 수신자 각각에 대해 알림 설정을 확인하고, 허용되면 인앱 알림을 저장한다. */
    private void notifyEach(List<Long> userIds, NotificationType type, Map<String, Object> payload) {
        if (userIds.isEmpty()) {
            return;
        }
        String payloadJson = writePayload(payload);
        for (Long userId : userIds) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || !isAllowed(user.getNotificationPrefs(), type)) {
                continue;   // 설정 off → 인앱·FCM 둘 다 발송 안 함
            }
            notificationRepository.save(Notification.builder()
                    .userId(userId)
                    .type(type)
                    .payload(payloadJson)
                    .build());
            // TODO(FCM): Firebase 프로젝트 생성 후, user.getFcmToken()이 있으면 여기서 FCM 발송.
            //            무효 토큰(UNREGISTERED/INVALID_ARGUMENT) 응답이면 user.clearFcmToken().
            //            FCM 성공/실패와 무관하게 위 인앱 저장은 항상 유지한다.
        }
    }

    /** 수신자의 notification_prefs(마스터 + 타입별 토글)를 확인. 미설정/깨진 값이면 전체 허용. */
    private boolean isAllowed(String prefsJson, NotificationType type) {
        NotificationSettingsResponse prefs = parsePrefs(prefsJson);
        if (!prefs.allowAll()) {
            return false;   // 마스터 off → 아무 알림도 생성 안 함
        }
        return switch (type) {
            case NEW_CYCLE, CYCLE_COMPLETED -> prefs.activity().followShot();
            case DEADLINE -> prefs.activity().deadlineVote();
            case MEMBER_JOIN -> prefs.etc().memberJoin();
            default -> true;   // 2차 타입은 별도 토글 없음(현재 미발송)
        };
    }

    private NotificationSettingsResponse parsePrefs(String prefsJson) {
        if (prefsJson == null || prefsJson.isBlank()) {
            return NotificationSettingsResponse.allOn();
        }
        try {
            return objectMapper.readValue(prefsJson, NotificationSettingsResponse.class);
        } catch (JsonProcessingException e) {
            return NotificationSettingsResponse.allOn();
        }
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(Long userId, String category, int size) {
        Collection<NotificationType> types = resolveTypes(category);

        List<NotificationItem> items = notificationRepository
                .findByUserIdAndTypeInOrderByCreatedAtDesc(userId, types, PageRequest.of(0, size))
                .stream()
                .map(this::toItem)
                .toList();

        long unreadCount = notificationRepository.countByUserIdAndReadAtIsNull(userId);
        return new NotificationListResponse(items, unreadCount);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.NOTIFICATION_FORBIDDEN);   // 본인 알림만
        }

        notification.markAsRead(LocalDateTime.now());   // 이미 읽었으면 멱등(변화 없음)
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        // 안읽음이 0개여도 정상 수행(멱등)
        notificationRepository.markAllAsRead(userId, LocalDateTime.now());
    }

    private Collection<NotificationType> resolveTypes(String category) {
        if ("activity".equalsIgnoreCase(category)) {
            return ACTIVITY_TYPES;
        }
        if ("etc".equalsIgnoreCase(category)) {
            return ETC_TYPES;
        }
        return EnumSet.allOf(NotificationType.class);   // all(기본)
    }

    private NotificationItem toItem(Notification n) {
        Object payload;
        try {
            payload = objectMapper.readValue(n.getPayload(), Object.class);
        } catch (JsonProcessingException e) {
            payload = Map.of();   // 깨진 payload는 빈 객체로
        }
        return new NotificationItem(
                n.getId(), n.getType().name(), payload, n.getReadAt(), n.getCreatedAt());
    }
}
