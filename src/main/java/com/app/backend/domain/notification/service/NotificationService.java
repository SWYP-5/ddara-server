package com.app.backend.domain.notification.service;

import com.app.backend.domain.notification.dto.NotificationItem;
import com.app.backend.domain.notification.dto.NotificationListResponse;
import com.app.backend.domain.notification.entity.Notification;
import com.app.backend.domain.notification.entity.NotificationType;
import com.app.backend.domain.notification.repository.NotificationRepository;
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

    public NotificationService(NotificationRepository notificationRepository,
                               ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
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
