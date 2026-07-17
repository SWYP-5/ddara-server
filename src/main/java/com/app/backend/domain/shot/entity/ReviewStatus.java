package com.app.backend.domain.shot.entity;

// 신고 검토 상태. 신고 접수 시 UNDER_REVIEW, 운영 판정으로 ACTIVE(복원) 또는 REMOVED(삭제)
public enum ReviewStatus {
    ACTIVE, UNDER_REVIEW, REMOVED
}