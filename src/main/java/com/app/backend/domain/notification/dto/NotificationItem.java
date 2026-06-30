package com.app.backend.domain.notification.dto;

import java.time.LocalDateTime;

/** 알림 1건. payload는 type별 구조가 다른 JSON 객체. */
public record NotificationItem(
        Long id,
        String type,
        Object payload,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
}
