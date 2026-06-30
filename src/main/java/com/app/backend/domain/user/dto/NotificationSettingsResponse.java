package com.app.backend.domain.user.dto;

/**
 * 알림 설정(U-03/04). users.notification_prefs(JSON)와 동일 구조.
 * 미설정(null)이면 {@link #allOn()} 전체 true 기본값.
 */
public record NotificationSettingsResponse(
        boolean allowAll,
        Activity activity,
        Etc etc
) {
    public record Activity(boolean followShot, boolean deadlineVote) {
    }

    public record Etc(boolean memberJoin) {
    }

    /** 미설정 기본값 — 전부 켜짐(true). */
    public static NotificationSettingsResponse allOn() {
        return new NotificationSettingsResponse(true, new Activity(true, true), new Etc(true));
    }
}
