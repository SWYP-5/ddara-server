package com.app.backend.domain.notification.service;

import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.notification.dto.NotificationItem;
import com.app.backend.domain.notification.dto.NotificationListResponse;
import com.app.backend.domain.notification.entity.Notification;
import com.app.backend.domain.notification.entity.NotificationType;
import com.app.backend.domain.notification.repository.NotificationRepository;
import com.app.backend.domain.shot.entity.Shot;
import com.app.backend.domain.shot.entity.ShotType;
import com.app.backend.domain.shot.repository.ShotRepository;
import com.app.backend.domain.user.dto.NotificationSettingsResponse;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

    // 시각은 KST 오프셋(+09:00)을 붙여 내보낸다 (프론트가 UTC로 오해해 9시간 어긋나는 것 방지)
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final ShotRepository shotRepository;
    private final FcmService fcmService;

    /** 모임 관련 알림(모임참여·마감임박)에 쓰는 앱 로고 S3 URL. UploadService와 동일한 형식으로 조합. */
    private final String logoUrl;

    public NotificationService(NotificationRepository notificationRepository,
                               ObjectMapper objectMapper,
                               MembershipRepository membershipRepository,
                               UserRepository userRepository,
                               ShotRepository shotRepository,
                               FcmService fcmService,
                               @Value("${aws.s3.bucket}") String bucket,
                               @Value("${aws.s3.region}") String region) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.shotRepository = shotRepository;
        this.fcmService = fcmService;
        this.logoUrl = "https://" + bucket + ".s3." + region + ".amazonaws.com/assets/ddara-logo.png";
    }

    // ===== 알림 생성(INSERT) 내부 인터페이스 — 회차/모임 흐름(오지원)에서 호출 (부록 B) =====
    // 각 메서드: 수신자별 notification_prefs 확인 → 켜져 있으면 인앱 알림 저장 + FCM 푸시 발송.

    /** 회차 시작 → 모임 멤버 전원. */
    @Transactional
    public void createNewCycle(Long groupId, String groupName, Long cycleId, LocalDateTime deadlineAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("groupId", groupId);
        payload.put("groupName", groupName);
        payload.put("cycleId", cycleId);
        payload.put("deadlineAt", deadlineAt.atZone(SEOUL).toOffsetDateTime().toString());   // 마감시각
        payload.put("imageUrl", starterShotImageUrl(cycleId));   // 개인 관련 → 스타터 원본 가이드샷
        notifyEach(activeMemberIds(groupId), NotificationType.NEW_CYCLE, payload);
    }

    /** 회차 마감 → 모임 멤버 전원. */
    @Transactional
    public void createCycleCompleted(Long groupId, String groupName, Long cycleId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("groupId", groupId);
        payload.put("groupName", groupName);
        payload.put("cycleId", cycleId);
        payload.put("imageUrl", starterShotImageUrl(cycleId));   // 개인 관련 → 스타터 원본 가이드샷
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
        payload.put("imageUrl", logoUrl);   // 모임 관련 → 앱 로고
        notifyEach(recipients, NotificationType.MEMBER_JOIN, payload);
    }

    /**
     * 마감 임박(CycleScheduler가 매분 호출) → 아직 인증샷을 올리지 않은 미참여 멤버.
     * remainingMinutes = 남은 시간 단계(60/30/5/1분). 같은 회차·같은 단계는 1회만 발송.
     */
    @Transactional
    public void createDeadline(Long groupId, String groupName, Long cycleId,
                               LocalDateTime deadlineAt, int remainingMinutes) {
        // 스케줄러가 1분마다 재호출하므로, 같은 회차·같은 단계에 이미 생성했으면 스킵(중복 발송 방지)
        if (notificationRepository.existsByTypeAndPayloadContaining(
                NotificationType.DEADLINE,
                "\"cycleId\":" + cycleId + ",\"remainingMinutes\":" + remainingMinutes + ",")) {
            return;
        }
        List<Long> recipients = activeMemberIds(groupId).stream()
                .filter(id -> !shotRepository.existsByCycleIdAndUserIdAndDeletedAtIsNull(cycleId, id))
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("groupId", groupId);
        payload.put("groupName", groupName);
        payload.put("cycleId", cycleId);
        payload.put("remainingMinutes", remainingMinutes);
        payload.put("deadlineAt", deadlineAt.atZone(SEOUL).toOffsetDateTime().toString());
        payload.put("imageUrl", logoUrl);   // 모임 관련 → 앱 로고
        notifyEach(recipients, NotificationType.DEADLINE, payload);
    }

    /**
     * 회차 스타터가 시작 때 올린 원본 가이드샷(따라 찍을 사진) URL. 개인 관련 알림(회차시작·따라찍기완료)에 씀.
     * 해당 회차의 STARTER shot이 없으면 null.
     */
    private String starterShotImageUrl(Long cycleId) {
        if (cycleId == null) {
            return null;
        }
        return shotRepository.findByCycleIdAndType(cycleId, ShotType.STARTER)
                .map(Shot::getImageUrl)
                .orElse(null);
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
            // FCM 푸시 발송 — 실패해도 예외를 던지지 않으므로(FcmService 내부 처리)
            // 위 인앱 저장은 항상 유지된다. 무효 토큰이면 FcmService가 user의 토큰을 비운다.
            fcmService.sendTo(user, pushTitle(type), pushBody(type, payload), pushData(type, payload));
        }
    }

    /** 알림 타입별 푸시 제목. */
    private String pushTitle(NotificationType type) {
        return switch (type) {
            case NEW_CYCLE -> "새 따라찍기 시작";
            case CYCLE_COMPLETED -> "따라찍기 완료";
            case MEMBER_JOIN -> "모임 참여";
            case DEADLINE -> "마감 임박";
            default -> "따라 알림";   // 2차 타입 대비
        };
    }

    /** 알림 타입별 푸시 본문. payload의 모임/합류자 이름을 활용. */
    private String pushBody(NotificationType type, Map<String, Object> payload) {
        String groupName = String.valueOf(payload.getOrDefault("groupName", "모임"));
        return switch (type) {
            case NEW_CYCLE -> "'" + groupName + "'에서 새 따라찍기가 시작됐어요!";
            case CYCLE_COMPLETED -> "'" + groupName + "'에서 따라찍기가 완료되었어요!";
            case MEMBER_JOIN -> payload.getOrDefault("actorNickname", "친구") + "님이 '" + groupName + "' 모임에 합류했어요";
            case DEADLINE -> {
                int remaining = ((Number) payload.getOrDefault("remainingMinutes", 60)).intValue();
                String left = remaining >= 60 ? (remaining / 60) + "시간" : remaining + "분";
                yield "'" + groupName + "' 따라찍기가 " + left + " 후 마감돼요. 아직 안찍었죠?";
            }
            default -> "'" + groupName + "'에 새로운 소식이 있어요.";
        };
    }

    /** 푸시 클릭 시 앱이 화면 이동에 쓸 data(모두 문자열이어야 함 — FCM 규격). */
    private Map<String, String> pushData(NotificationType type, Map<String, Object> payload) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", type.name());
        payload.forEach((k, v) -> data.put(k, String.valueOf(v)));
        return data;
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
                n.getId(), n.getType().name(), payload,
                toKstOffset(n.getReadAt()), toKstOffset(n.getCreatedAt()));
    }

    /** LocalDateTime(KST 벽시계)을 KST 오프셋(+09:00)이 붙은 OffsetDateTime으로 변환. null 허용. */
    private OffsetDateTime toKstOffset(LocalDateTime ldt) {
        return ldt == null ? null : ldt.atZone(SEOUL).toOffsetDateTime();
    }
}
