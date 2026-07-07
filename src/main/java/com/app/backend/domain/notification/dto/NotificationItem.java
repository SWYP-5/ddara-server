package com.app.backend.domain.notification.dto;

import java.time.OffsetDateTime;

/** 알림 1건. payload는 type별 구조가 다른 JSON 객체. 시각은 KST 오프셋(+09:00) 포함. */
public record NotificationItem(
        Long id,
        String type,
        Object payload,
        OffsetDateTime readAt,
        OffsetDateTime createdAt
) {
}
