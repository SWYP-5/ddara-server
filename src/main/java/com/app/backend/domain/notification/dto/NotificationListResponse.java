package com.app.backend.domain.notification.dto;

import java.util.List;

/** 알림 목록 조회(N-01) 응답. unreadCount는 전체 안읽음 합계. */
public record NotificationListResponse(
        List<NotificationItem> items,
        long unreadCount
) {
}
