package com.app.backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * FCM 토큰 등록(U-06) 요청. 유저당 1개 저장(덮어쓰기).
 * 빈 값이면 검증 실패 → 400 INVALID_INPUT.
 */
public record FcmTokenRequest(
        @NotBlank String fcmToken
) {
}
