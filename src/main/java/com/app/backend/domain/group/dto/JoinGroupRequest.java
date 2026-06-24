package com.app.backend.domain.group.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinGroupRequest(
        @NotBlank(message = "초대 코드는 필수입니다.")
        String inviteCode
) {
}
