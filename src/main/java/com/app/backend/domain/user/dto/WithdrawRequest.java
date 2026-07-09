package com.app.backend.domain.user.dto;

public record WithdrawRequest(
        String appleAuthorizationCode
) {
}
