package com.app.backend.domain.notification.dto;

import java.util.List;

/** 알림 목록 조회(N-01) 응답. */
public record NotificationListResponse(
        List<NotificationItem> items,   // 알림 목록 (최신순 정렬)
        long unreadCount                // 안 읽은 알림 총 개수 (앱의 빨간 뱃지 숫자로 쓰임)
) {
}
