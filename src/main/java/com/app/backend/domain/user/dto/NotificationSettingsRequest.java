package com.app.backend.domain.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 알림 설정 변경(U-04) 요청. 전체 교체 방식 — 모든 boolean 필드 필수.
 * 누락 시 검증 실패 → 400 INVALID_INPUT.
 */
public record NotificationSettingsRequest(
        @NotNull Boolean allowAll,
        @NotNull @Valid Activity activity,
        @NotNull @Valid Etc etc
) {
    public record Activity(
            @NotNull Boolean followShot,
            @NotNull Boolean deadlineVote
    ) {
    }

    public record Etc(
            @NotNull Boolean memberJoin
    ) {
    }
}
