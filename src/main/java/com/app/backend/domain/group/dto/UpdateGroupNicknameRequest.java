package com.app.backend.domain.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateGroupNicknameRequest(
        @NotBlank(message = "모임 닉네임은 필수입니다.")
        @Size(min = 2, max = 10, message = "모임 닉네임은 2~10자입니다.")
        String nickname
) {
}