package com.app.backend.domain.notification.dto;

import java.time.OffsetDateTime;

/** 알림 1건. payload는 type별 구조가 다른 JSON 객체. 시각은 KST 오프셋(+09:00) 포함. */
public record NotificationItem(
        Long id,                    // 알림 고유 번호 (읽음 처리 N-02 할 때 이 id를 씀)
        String type,                // 알림 종류: NEW_CYCLE(회차시작)/CYCLE_COMPLETED(완료)/MEMBER_JOIN(합류)/DEADLINE(마감임박)
        Object payload,             // 알림에 딸린 데이터(type별로 다름): groupName·imageUrl·deadlineAt 등 (프론트 표시용)
        OffsetDateTime readAt,      // 읽은 시각(+09:00). null이면 아직 안 읽음
        OffsetDateTime createdAt    // 알림이 생성된 시각(+09:00)
) {
}
