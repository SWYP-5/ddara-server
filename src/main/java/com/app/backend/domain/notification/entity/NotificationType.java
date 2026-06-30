package com.app.backend.domain.notification.entity;

/** 알림 종류. 1차 4종 + 2차 3종(미리 정의해 2차 때 스키마 변경 불필요). */
public enum NotificationType {
    // 1차
    NEW_CYCLE,
    CYCLE_COMPLETED,
    MEMBER_JOIN,
    DEADLINE,
    // 2차
    VOTE_START,
    VOTE_RESULT,
    REACTION
}
